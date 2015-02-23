package com.neovahealth.nhADT

import ca.uhn.hl7v2.DefaultHapiContext
import ca.uhn.hl7v2.model.Message
import ca.uhn.hl7v2.util.idgenerator.InMemoryIDGenerator

import com.neovahealth.nhADT.exceptions.ADTExceptions
import com.neovahealth.nhADT.rules.RuleParser
import com.neovahealth.nhADT.utils.{Action, ConfigHelper}
import com.tactix4.t4openerp.connector.OEConnector

import com.typesafe.scalalogging.slf4j.LazyLogging

import org.apache.camel.component.redis.RedisConstants
import org.apache.camel.model.IdempotentConsumerDefinition
import org.apache.camel.model.dataformat.HL7DataFormat
import org.apache.camel.scala.dsl.SIdempotentConsumerDefinition
import org.apache.camel.scala.dsl.builder.RouteBuilder
import org.apache.camel.{Exchange, LoggingLevel, Processor}

import scala.concurrent.ExecutionContext
import scala.util.control.Exception._

import scalaz.std.string._
import scalaz.syntax.monoid._
import scalaz.syntax.std.option._


class ADTInRoute() extends RouteBuilder with EObsCalls with ADTErrorHandling with ADTProcessing with ADTExceptions with LazyLogging with RuleParser{

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  implicit def idem2Sidem(i:IdempotentConsumerDefinition):SIdempotentConsumerDefinition = SIdempotentConsumerDefinition(i)(this)

  lazy val session = new OEConnector(ConfigHelper.protocol, ConfigHelper.host, ConfigHelper.port)
    .startSession(ConfigHelper.username, ConfigHelper.password, ConfigHelper.database)

  val hl7:HL7DataFormat = new HL7DataFormat()
  hl7.setValidate(false)

  val ctx = new DefaultHapiContext()
  //the default FileBased ID Generator starts failing with multiple threads
  ctx.getParserConfiguration.setIdGenerator(new InMemoryIDGenerator())

  val updateVisitRoute = "direct:updateOrCreateVisit"
  val updatePatientRoute = "direct:updateOrCreatePatient"
  val msgHistory = "msgHistory"
  val detectDuplicates = "direct:detectDuplicates"
  val detectUnsupportedMsg = "direct:detectUnsupportedMsgs"
  val detectUnsupportedWards = "direct:detectUnsupportedWards"
  val detectHistoricalMsg = "direct:detectHistoricalMsg"
  val setBasicHeaders = "direct:setBasicHeaders"
  val setConflictHeaders = "direct:setConflictHeaders"
  val detectIdConflict = "direct:idConflictCheck"
  val detectVisitConflict = "direct:visitConflictCheck"
  val persistTimestamp = "direct:persistTimestamp"
  val getTimestamp = "direct:getVisitTimestamp"

  val A08Route = "direct:A08"
  val A31Route = "direct:A31"
  val A05Route = "direct:A05"
  val A28Route = "direct:A28"
  val A01Route = "direct:A01"
  val A02Route = "direct:A02"
  val A03Route = "direct:A03"
  val A11Route = "direct:A11"
  val A12Route = "direct:A12"
  val A13Route = "direct:A13"
  val A40Route = "direct:A40"


  val rules:List[Rule] = ConfigHelper.ruleFile.map(parse(line,_).get)

  def evalExpression(z:Expr)(implicit e:Exchange): Boolean = z.value(getField(_))

  lazy val processRules:Processor = new Processor {
    override def process(e: Exchange): Unit = {
      val result = allCatch opt rules.par.collect {
        case (action, exec, fail) if action != evalExpression(exec)(e) => fail
      }

      result.map(r => {
        if(r.nonEmpty) {
          val c = r.reduce(_ |+| _)
          throw new ADTRuleException(c.msg)
        }
      })
    }
  }

  from("hl7listener") ==> {
    unmarshal(hl7)
    setHeader("JMSXGroupID", (e: Exchange) => ~getHospitalNumber(e))
    log(LoggingLevel.INFO, "recieved ${in.header.CamelHL7TriggerEvent} for ${in.header.JMSXGroupID}")
    process(e => {
      val m = e.in[Message]
      m.setParser(ctx.getPipeParser)
      e.in = m
    })
    choice {
      when(_ => ConfigHelper.autoAck) {
        inOnly {
          to("activemq-in")
        }
        transform(_.in[Message].generateACK())
      }
      otherwise {
        to("activemq-in")
      }
    }
  }routeId "Listener to activemq"

  from("activemq-in") ==> {
    log(LoggingLevel.INFO, "processing ${in.header.CamelHL7TriggerEvent} for ${in.header.JMSXGroupID}")
    process(processRules)
    bean(RoutingSlipBean())
    to(msgHistory)
    transform(_.in[Message].generateACK())
  } routeId "Main Route"


  def handleUnknownVisit(createAction: Exchange => Unit) = (e: Exchange) => {
    ConfigHelper.unknownVisitAction match {
      case Action.IGNORE => logger.warn("Visit doesn't exist - ignoring")
      case Action.ERROR  => throw new ADTUnknownVisitException("Unknown visit")
      case Action.CREATE => createAction(e)
    }
  }

  def handleUnknownPatient(createAction: Exchange => Unit) = (e: Exchange) => {
    ConfigHelper.unknownPatientAction match {
      case Action.IGNORE => logger.warn("Patient doesn't exist - ignoring")
      case Action.ERROR  => throw new ADTUnknownPatientException("Unknown patient")
      case Action.CREATE => createAction(e)
    }
  }

  from(persistTimestamp) ==> {
    when(getTimestamp(_)) {
      setHeader(RedisConstants.KEY, (e: Exchange) => ~getVisitName(e))
      setHeader(RedisConstants.VALUE, (e: Exchange) => ~getTimestamp(e))
      to("toRedis")
    }
  } routeId "Timestamp to redis"

  from(getTimestamp) ==> {
    setHeader(RedisConstants.COMMAND,(e:Exchange) => constant("GET")(e))
    setHeader(RedisConstants.KEY,(e:Exchange) => ~getVisitName(e))
    enrich("fromRedis",new AggregateLastModTimestamp)
    process(e => getTimestamp(e))
    log(LoggingLevel.INFO,"Timestamp from redis : ${header.lastModTimestamp}. Timestamp from message: ${header.timestamp}")
  } routeId "Timestamp from redis"

  updatePatientRoute ==> {
    choice {
      when(patientExists(_)) {
        process(patientUpdate(_))
      }
      otherwise {
        process(handleUnknownPatient(patientNew(_)))
      }
    }
  } routeId "Create/Update Patient"

  updateVisitRoute ==> {
    choice {
      when(visitExists(_)) {
        process(visitUpdate(_))
      }
      when(e => getVisitName(e).isDefined && !visitExists(e)) {
        process(handleUnknownVisit(visitNew(_)))
      }
      otherwise {
        log(LoggingLevel.INFO, "Message has no visit identifier - can not update/create visit")
      }

    }
  } routeId "Create/Update Visit"

  A01Route ==> {
    process(visitNew(_))
    -->(persistTimestamp)
  } routeId "A01"


  A11Route ==> {
    when(visitExists(_)) {
      process(cancelVisitNew(_))
    } otherwise {
      process(handleUnknownVisit( e => {
        visitNew(e)
        cancelVisitNew(e)
      }))
    }
  } routeId "A11"

  A02Route ==> {
    choice {
      when(visitExists(_)) {
        process(patientTransfer(_))
      }
      otherwise {
        process(handleUnknownVisit(implicit e => {
          visitNew
          patientTransfer
        }))
      }
    }
    -->(persistTimestamp)
  } routeId "A02"


  A12Route ==> {
    when(visitExists(_)){
      process(cancelPatientTransfer(_))
    } otherwise {
      process(handleUnknownVisit(implicit e => {
        visitNew
        cancelPatientTransfer
      }))

    }
  } routeId "A12"

  A03Route ==> {
    when(e => visitExists(e)) {
      process(patientDischarge(_))
    } otherwise {
      process(handleUnknownVisit(implicit e => {
        visitNew
        patientDischarge
      }))
    }
    -->(persistTimestamp)
  } routeId "A03"

  A13Route ==> {
    when(visitExists(_)) {
      process(cancelPatientDischarge(_))
    } otherwise {
      process(e => handleUnknownVisit( e =>{
        visitNew(e)
      }))
    }
  } routeId "A13"

  A05Route ==> {
    process(patientNew(_))
  } routeId "A05"

  A28Route ==> {
    process(patientNew(_))
  } routeId "A28"

  A40Route ==> {
    process(patientMerge(_))
  } routeId "A40"

  A08Route ==> {
    -->(getTimestamp)
    when(refersToCurrentAction(_)){
        -->(updatePatientRoute)
        -->(updateVisitRoute)
    } otherwise {
      log(LoggingLevel.INFO,"Ignoring Historical Message for ${header.JMSXGroupID}")
    }
  } routeId "A08"

  A31Route ==> {
    -->(updatePatientRoute)
  } routeId "A31"

  def refersToCurrentAction(implicit e:Exchange): Boolean = {
    val r = for {
      l <- getHeader[String]("lastModTimestamp")
      t <- getHeader[String]("timestamp")
    } yield t >= l

    r | true
  }
}

