package com.tactix4.t4ADT

import org.apache.camel.scala.dsl.builder.RouteBuilder
import org.apache.camel.model.dataformat.HL7DataFormat

import ca.uhn.hl7v2.util.Terser
import ca.uhn.hl7v2.model.Message

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.Exception._

import org.joda.time.format.DateTimeFormat

import com.tactix4.t4skr.T4skrConnector
import com.tactix4.t4ADT.utils.Instrumented
import org.apache.camel.Exchange
import org.apache.camel.scala.dsl.{DSL, SIdempotentConsumerDefinition}
import org.apache.camel.processor.idempotent.jdbc.JdbcMessageIdRepository
import org.apache.camel.language.Bean
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository
import nl.grons.metrics.scala.Timer
import scala.collection.immutable.HashMap

//TODO: Convert all camelCase fields in config files to not_camel_case
//TODO: create patientExists method in connector
//TODO: add a create table method for sql store

/**
 * A Camel Route for receiving ADT messages over an MLLP connector
 * via the mina2 component, validating them, then calling the associated
 * t4skrConnetor methods and returning an appropriate ack
 * Note: we block on the async t4skrConnector methods because the mina connection
 * is synchronous
 */

class ADTApplicationException(msg:String, cause:Throwable=null) extends Throwable(msg,cause)
class ADTFieldException(msg:String,cause:Throwable=null)extends Throwable(msg,cause)
class ADTUnsupportedMessageException(msg:String=null,cause:Throwable=null)extends Throwable(msg,cause)
class ADTDuplicateMessageException(msg:String=null,cause:Throwable=null)extends Throwable(msg,cause)

class ADTInRoute(implicit val terserMap: Map[String,Map[String, String]],
                 val protocol: String,
                 val host: String,
                 val port: Int,
                 val username: String,
                 val password: String,
                 val database: String,
                 val fromDateFormat: String,
                 val toDateFormat: String,
                 val timeOutMillis: Int,
                 val redeliveryDelay: Long,
                 val maximumRedeliveries: Int,
                 val msgStoreTableName: String) extends RouteBuilder with ADTProcessing with ADTErrorHandling with Instrumented{



  val connector = new T4skrConnector(protocol, host, port).startSession(username,password,database)//.map(s => {s.openERPSession.context.setTimeZone("Europe/London"); s})

  val fromDateTimeFormat = DateTimeFormat.forPattern(fromDateFormat)
  val toDateTimeFormat = DateTimeFormat.forPattern(toDateFormat)
  val datesToParse = List("dob","visitStartDateTime")

  val triggerEventHeader = "CamelHL7TriggerEvent"
  val hl7 = new HL7DataFormat()
  hl7.setValidate(false)


  val patientUpdateTimer = metrics.timer("patientUpdate")
  val patientNewTimer = metrics.timer("patientNew")
  val patientMergeTimer = metrics.timer("patientMerge")
  val visitNewTimer = metrics.timer("visitNew")
  val visitUpdateTimer = metrics.timer("visitUpdate")
  val patientTransferTimer = metrics.timer("patientTransfer")
  val patientDischargeTimer = metrics.timer("patientDischarge")

  val metricMap = Map(
    "A08" -> (patientUpdateTimer, patientUpdate(_)),
    "A31" -> (patientUpdateTimer, patientUpdate(_)),
    "A28" -> (patientNewTimer, patientNew(_)),
    "A05" -> (patientNewTimer,patientNew(_)),
    "A40" -> (patientMergeTimer,patientMerge(_)),
    "A01" -> (visitNewTimer,visitNew(_)),
    "A02" -> (patientTransferTimer,patientTransfer(_)),
    "A03" -> (patientDischargeTimer,patientDischarge(_)),
    "A11" -> (visitUpdateTimer, visitUpdate(_)),
    "A12" -> (visitUpdateTimer,visitUpdate(_)),
    "A13" -> (visitUpdateTimer,visitUpdate(_))
  )

  def patientExists(hospitalNumber: String): Boolean = Await.result(connector.flatMap(_.getPatient(hospitalNumber)), 1000 millis).isDefined

  def matchMsg(t:String) = when(_.in(triggerEventHeader) == t) process(e => {metrics.meter(t).mark();metricMap(t)._1.time{e.in = metricMap(t)._2(e.in[Message])}})

  "hl7listener" ==> {
    process(_ =>  metrics.meter("AllMessages").mark() )
    unmarshal(hl7)
    process(e => setHeader("terser", new Terser(e.in[Message])))
    SIdempotentConsumerDefinition(idempotentConsumer(_.getIn.getHeader("CamelHL7MessageControl"))
      .messageIdRepositoryRef("messageIdRepo")
      .skipDuplicate(false)
      .removeOnFailure(false)
    )(this) {
      when(_.getProperty(Exchange.DUPLICATE_MESSAGE)) process(e => throw new ADTDuplicateMessageException())
      process(e => setHeader("NeedsUpdate",patientExists(e.in("terser").asInstanceOf[Terser].get("PID-3-1"))))
      choice {
        matchMsg("A08")
        matchMsg("A31")
        matchMsg("A28")
        matchMsg("A05")
        matchMsg("A40")
        matchMsg("A01")
        matchMsg("A02")
        matchMsg("A03")
        matchMsg("A11")
        matchMsg("A12")
        matchMsg("A13")
        otherwise process(e =>  {
          metrics.meter("Unsupported").mark()
          throw new ADTUnsupportedMessageException("Unsupported message type: " + e.in(triggerEventHeader))
        })
      }

      when(_.in("NeedsUpdate") == true) process (e =>  patientUpdate(e.in[Message]))

      process(e => setHeader("msg",e.in[String]))
      to(s"sql:insert into $msgStoreTableName (id, type, timestamp, data) values (:#CamelHL7MessageControl,:#CamelHL7TriggerEvent,:#CamelHL7Timestamp,:#msg)")
    }
  }

  def extract(f : Terser => Map[String,String] => Future[_]) (implicit message:Message): Message = {
    implicit val terser = new Terser(message)
    implicit val mappings = getMappings(terser, terserMap)

    val result = allCatch either Await.result(f(terser)(mappings), timeOutMillis millis)

    result.left.map((error: Throwable) => throw new ADTApplicationException(error.getMessage, error))

    message.generateACK()
  }

  def patientMerge(implicit message:Message): Message = extract { implicit terser => implicit mappings=>
    val requiredFields = validateRequiredFields(List("otherId", "oldOtherId"))
    connector.flatMap(_.patientMerge(requiredFields("otherId"), requiredFields("oldOtherId")))
  }

  def patientTransfer(implicit message:Message): Message = extract { implicit terser => implicit mappings=>
    val i = getIdentifier
    val w = validateRequiredFields(List("wardId"))
    connector.flatMap(_.patientTransfer(i,w("wardId")))
  }

  def patientUpdate(implicit message:Message) :Message = extract {implicit terser => implicit map =>
    val i = getIdentifier
    val o = validateAllOptionalFields(i)
    connector.flatMap(_.patientUpdate(i,o))
  }

  def patientDischarge(implicit message: Message)  = extract{implicit t => implicit m =>
    val i = getIdentifier
    connector.flatMap(_.patientDischarge(i))
  }

  def patientNew(implicit message: Message) = extract{implicit t => implicit m =>
    val i = getIdentifier
    connector.flatMap(_.patientNew(i))
  }

  def visitNew(implicit message: Message) = extract{implicit t => implicit m =>
    val requiredFields =  validateRequiredFields(List("wardId","visitId","visitStartDateTime"))
    connector.flatMap(_.visitNew(getIdentifier,requiredFields("wardId"), requiredFields("visitId"), requiredFields("visitStartDateTime")))
  }

  def visitUpdate(implicit message:Message) = extract{ implicit t => implicit m =>
    Future.successful()
  }

}
