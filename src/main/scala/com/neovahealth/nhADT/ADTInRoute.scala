package com.neovahealth.nhADT

import ca.uhn.hl7v2.model.Message
import ca.uhn.hl7v2.util.Terser
import com.neovahealth.nhADT.exceptions.ADTExceptions
import com.neovahealth.nhADT.utils.{Action, ConfigHelper}
import com.tactix4.t4openerp.connector.OEConnector
import com.typesafe.scalalogging.slf4j.LazyLogging
import org.apache.camel.component.redis.RedisConstants
import org.apache.camel.model.IdempotentConsumerDefinition
import org.apache.camel.scala.dsl.SIdempotentConsumerDefinition
import org.apache.camel.scala.dsl.builder.RouteBuilder
import org.apache.camel.{Exchange, LoggingLevel}

import scala.concurrent.ExecutionContext.Implicits.global
import scalaz.Scalaz._


class ADTInRoute() extends RouteBuilder with EObsCalls with ADTErrorHandling with ADTProcessing with ADTExceptions with LazyLogging {

  implicit def idem2Sidem(i:IdempotentConsumerDefinition):SIdempotentConsumerDefinition = SIdempotentConsumerDefinition(i)(this)

  val unknownPatientAction: Action.Value = ConfigHelper.unknownPatientAction
  val unknownVisitAction: Action.Value = ConfigHelper.unknownVisitAction


  val connector = new OEConnector(ConfigHelper.protocol, ConfigHelper.host, ConfigHelper.port)
    .startSession(ConfigHelper.username, ConfigHelper.password, ConfigHelper.database)

  val updateVisitRoute = "direct:updateOrCreateVisit"
  val updatePatientRoute = "direct:updateOrCreatePatient"
  val msgHistory = "msgHistory"
  val detectDuplicates = "direct:detectDuplicates"
  val detectUnsupportedMsg = "direct:detectUnsupportedMsgs"
  val detectUnsupportedWards = "direct:detectUnsupportedWards"
  val detectHistoricalMsg = "direct:detectHistoricalMsg"
  val setBasicHeaders = "direct:setBasicHeaders"
  val setExtraHeaders = "direct:setExtraHeaders"
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

  from("hl7listener") --> "activemq-in" routeId "Listener to activemq"


  "activemq-in" ==> {
    unmarshal(hl7)
    idempotentConsumer(_.in("CamelHL7MessageControl")).messageIdRepositoryRef("messageIdRepo").skipDuplicate(false).removeOnFailure(false){
      -->(setBasicHeaders)
      multicast.parallelProcessing.streaming.stopOnException {
        -->(detectDuplicates)
        -->(detectUnsupportedMsg)
        -->(detectUnsupportedWards)
      }
      -->(setExtraHeaders)
      multicast.parallelProcessing.streaming.stopOnException {
        -->(detectIdConflict)
        -->(detectVisitConflict)
        -->(detectHistoricalMsg)
      }
      bean(new RoutingSlipBean())
      -->(msgHistory)
      transform(_.in[Message].generateACK())
      marshal(hl7)
    }
  } routeId "Main Route"

  detectHistoricalMsg ==> {
    when(implicit e => msgType(e) != "A03" && hasHeader("dischargeDate")) {

      process(e => {
        println("here")
      })
      throwException(new ADTHistoricalMessageException("Historical message detected"))
    }
  } routeId "Detect Historical Message"

  detectDuplicates ==>{
    when(_.getProperty(Exchange.DUPLICATE_MESSAGE)) throwException new ADTDuplicateMessageException("Duplicate message")
  } routeId "Detect Duplicates"

  detectUnsupportedMsg ==> {
    when(e => !(ConfigHelper.supportedMsgTypes contains e.in(triggerEventHeader).toString)) throwException new ADTUnsupportedMessageException("Unsupported msg type")
  } routeId "Detect Unsupported Msg"

  detectUnsupportedWards ==> {
    when(e => !isSupportedWard(e)) throwException new ADTUnsupportedWardException("Unsupported ward")
  } routeId "Detect Unsupported Wards"

  detectIdConflict ==> {
     when(e => {
        val p1 = getPatientLinkedToHospitalNo(e)
        val p2 = getPatientLinkedToNHSNo(e)
        p1.isDefined && p2.isDefined && p1 != p2
      }) {
        process( e => throw new ADTConsistencyException("Hospital number: " +e.in("hospitalNo") + " is linked to patient: "+ e.in("patientLinkedToHospitalNo") +
          " but NHS number: " + e.in("NHSNo") + "is linked to patient: " + e.in("patientLinkedToNHSNo")))
      }
  } routeId "Detect Id Conflict"

  detectVisitConflict ==> {
     //check for conflict with visits
      when(e => {
        val pv = getPatientLinkedToVisit(e)
        val ph = getPatientLinkedToHospitalNo(e)
        pv.isDefined && pv != ph
      }){
        process( e => throw new ADTConsistencyException("Hospital number: " + e.in("hospitalNo") + " is linked to patient: " + e.in("patientLinkedToHospitalNo") + " " +
          " but visit: " + e.in("visitName") + " is linked to patient: " + e.in("patientLinkedToVisit") + ""))
      }
  } routeId "Detect Visit Conflict"

 setBasicHeaders ==> {
   process(implicit e => {
     val message = e.in[Message]
     val t = new Terser(message)

     setHeaderValue("hospitalNoString", ~getHospitalNumber(t)) // String
     setHeaderValue("visitNameString", ~getVisitName(t)) // String
     setHeaderValue("msgBody",e.getIn.getBody.toString) // String
     setHeaderValue("origMessage", message) // Message
     setHeaderValue("terser", t) // terser

     setHeaderValue("hospitalNo", getHospitalNumber(t))// Option[String]
     setHeaderValue("NHSNo", getNHSNumber(t)) // Option[String]
     setHeaderValue("visitName", getVisitName(t)) // Option[String]
     setHeaderValue("dischargeDate", getDischargeDate(t)) // Option[String]
     setHeaderValue("timestamp", getTimestamp(t)) // Option[String]
   })
 } routeId "Set Basic Headers"

  setExtraHeaders ==> {
    process(e => {
      val hid = getHeader("hospitalNo",e)
      val nhs = getHeader("NHSNo",e)
      val visitName = getHeader("visitName",e)
      val visitId = visitName.flatMap(getVisit)

      setHeaderValue("visitId", visitId)(e)
      setHeaderValue("patientLinkedToHospitalNo", hid.flatMap(getPatientByHospitalNumber))(e)
      setHeaderValue("patientLinkedToNHSNo", nhs.flatMap(getPatientByNHSNumber))(e)
      setHeaderValue("patientLinkedToVisit", visitId.flatMap(getPatientByVisitId))(e)
    })
  } routeId "Set Extra Headers"




  def handleUnknownVisit(createAction: Exchange => Unit) = (e: Exchange) => {
    unknownVisitAction match {
      case Action.IGNORE => logger.warn("Visit doesn't exist - ignoring")
      case Action.ERROR  => throw new ADTUnknownVisitException("Unknown visit")
      case Action.CREATE => createAction(e)
    }
  }

  def handleUnknownPatient(createAction: Exchange => Unit) = (e: Exchange) => {
    unknownPatientAction match {
      case Action.IGNORE => logger.warn("Patient doesn't exist - ignoring")
      case Action.ERROR  => throw new ADTUnknownVisitException("Unknown patient")
      case Action.CREATE => createAction(e)
    }
  }
  from(persistTimestamp) ==> {
    when(e => hasHeader("timestamp")(e)) {
      setHeader(RedisConstants.KEY, simple("${header.visitNameString}"))
      setHeader(RedisConstants.VALUE, e => s"${~getHeader[String]("timestamp",e)}")
      to("toRedis")
    }
  } routeId "Timestamp to redis"

  from(getTimestamp) ==> {
    setHeader(RedisConstants.COMMAND,"GET")
    setHeader(RedisConstants.KEY,simple("${header.visitNameString}"))
    enrich("fromRedis",new AggregateLastModTimestamp)
    log(LoggingLevel.INFO,"Last Mod Timestamp: ${header.lastModTimestamp} vs This message timestamp: ${header.timestamp}")
  } routeId "Timestamp from redis"

  updatePatientRoute ==> {
    choice {
      when(e => patientExists(e)) {
        process(e => patientUpdate(e))
      }
      otherwise {
        process(handleUnknownPatient(implicit e => {
          patientNew(e)
          //update patientLinkedToHospitalNo header
          val hid = getHeader("hospitalNo",e)
          setHeaderValue("patientLinkedToHospitalNo",hid.flatMap(getPatientByHospitalNumber))
        }))
      }
    }
  } routeId "Create/Update Patient"

  updateVisitRoute ==> {
    choice {
      when(e => visitExists(e)) {
        process(e => visitUpdate(e))
      }
      when(e => !visitExists(e) && hasHeader("visitName")(e)) {
        process(handleUnknownVisit(implicit e => {
          visitNew(e)
          //update the visitId header
          val visitName = getHeader("visitName",e)
          setHeaderValue("visitId", visitName.flatMap(getVisit))
        }))
      }
      //otherwise there is no visit information
      otherwise {
        log(LoggingLevel.WARN, "Message has no visit identifier - can not update/create visit")
      }

    }
  } routeId "Create/Update Visit"

  A01Route ==> {
    when(e => visitExists(e)) throwException new ADTFieldException("Visit already exists")
    process(e => visitNew(e))
    -->(persistTimestamp)
  } routeId "A01"


  A11Route ==> {
    when(e => visitExists(e)) {
      process(e => cancelVisitNew(e))
    } otherwise {
      process(handleUnknownVisit( e => {
        visitNew(e)
        cancelVisitNew(e)
      }))
    }
  } routeId "A11"

  A02Route ==> {
    choice {
      when(visitExists) {
        process(e => patientTransfer(e))
      }
      otherwise {
        process(e => handleUnknownVisit(e => {
          visitNew(e)
          patientTransfer(e)
        }))
      }
    }
    -->(persistTimestamp)
  } routeId "A02"


  A12Route ==> {
    when(e => visitExists(e)){
      process(e => cancelPatientTransfer(e))
    } otherwise {
      process(e => handleUnknownVisit(e => {
        visitNew(e)
        cancelPatientTransfer(e)
      }))

    }
  } routeId "A12"

  A03Route ==> {
    when(e => visitExists(e)) {
      process(e => patientDischarge(e))
    } otherwise {
      process(e => handleUnknownVisit(e => {
        visitNew(e)
        patientDischarge(e)
      }))
    }
    -->(persistTimestamp)
  } routeId "A03"

  A13Route ==> {
    when(e => visitExists(e)) {
      process(e => cancelPatientDischarge(e))
    } otherwise {
      process(e => handleUnknownVisit( e =>{
        visitNew(e)
      }))
    }
  } routeId "A13"

  A05Route ==> {
    when(e => patientExists(e)) process(e => throw new ADTApplicationException("Patient with hospital number: " + e.in("hospitalNo") + " already exists"))
    process(e => patientNew(e))
  } routeId "A05"

  A28Route ==> {
    when(e => patientExists(e)) process(e => throw new ADTApplicationException("Patient with hospital number: " + e.in("hospitalNo") + " already exists"))
    process(e => patientNew(e))
  } routeId "A28"

  A40Route ==> {
    when(e => !patientExists(e) || !mergeTargetExists(e)) throwException new ADTConsistencyException("Patients to merge did not exist")
    process(e => patientMerge(e))
  } routeId "A40"

  A08Route ==> {
    -->(getTimestamp)
    when(refersToCurrentAction){
        -->(updatePatientRoute)
        -->(updateVisitRoute)
    } otherwise {
      log(LoggingLevel.INFO,"Ignoring Historical Message: ${header.timestamp}")
    }
  } routeId "A08"

  A31Route ==> {
    -->(updatePatientRoute)
  } routeId "A31"

  def refersToCurrentAction(e:Exchange): Boolean = {
    val r = for {
      l <- getHeader[String]("lastModTimestamp",e)
      t <- getHeader[String]("timestamp",e)
    } yield t >= l

    r | true
  }
}

