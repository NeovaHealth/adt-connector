package com.tactix4.t4ADT

import ca.uhn.hl7v2.model.Message
import ca.uhn.hl7v2.util.Terser
import com.tactix4.t4ADT.exceptions.ADTExceptions
import com.tactix4.t4ADT.utils.{Action, ConfigHelper}
import com.tactix4.t4openerp.connector.OEConnector
import com.typesafe.scalalogging.slf4j.LazyLogging
import org.apache.camel.component.redis.RedisConstants
import org.apache.camel.model.IdempotentConsumerDefinition
import org.apache.camel.scala.dsl.SIdempotentConsumerDefinition
import org.apache.camel.scala.dsl.builder.RouteBuilder
import org.apache.camel.{Exchange, LoggingLevel}

import scala.concurrent.ExecutionContext.Implicits.global
import scalaz.Scalaz._


class ADTInRoute() extends RouteBuilder with T4skrCalls with ADTErrorHandling with ADTProcessing with ADTExceptions with LazyLogging {

  implicit def idem2Sidem(i:IdempotentConsumerDefinition):SIdempotentConsumerDefinition = SIdempotentConsumerDefinition(i)(this)

  type VisitName = String

  val unknownWardAction: Action.Value = ConfigHelper.unknownWardAction
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

  from("hl7listener") --> "activemq-in"


  "activemq-in" ==> {
    unmarshal(hl7)
    idempotentConsumer(_.in("CamelHL7MessageControl")).messageIdRepositoryRef("messageIdRepo").skipDuplicate(false).removeOnFailure(false){
      -->(setBasicHeaders)
      multicast.parallel.streaming.stopOnException {
        -->(detectDuplicates)
        -->(detectUnsupportedMsg)
        -->(detectUnsupportedWards)
      }
      -->(setExtraHeaders)
      multicast.parallel.streaming.stopOnException {
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
    when(e => msgType(e) != "A03" && e.getIn.getHeader("hasDischargeDate", false, classOf[Boolean])) {
      throwException(new ADTHistoricalMessageException("Historical message detected"))
    }

  }

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
   process(e => {
     val message = e.in[Message]
     val t = new Terser(message)

     e.getIn.setHeader("msgBody",e.getIn.getBody.toString)
     e.getIn.setHeader("origMessage", message)
     e.getIn.setHeader("terser", t)
     e.getIn.setHeader("hospitalNo", getHospitalNumber(t))
     e.getIn.setHeader("hospitalNoString", ~getHospitalNumber(t))
     e.getIn.setHeader("NHSNo", getNHSNumber(t))
     e.getIn.setHeader("eventReasonCode",getEventReasonCode(t))
     e.getIn.setHeader("visitName", getVisitName(t))
     e.getIn.setHeader("visitNameString", ~getVisitName(t))
     e.getIn.setHeader("hasDischargeDate", hasDischargeDate(t))
     e.getIn.setHeader("timestamp", ~getTimestamp(t))
   })
 } routeId "Set Basic Headers"

  setExtraHeaders ==> {
    process(e => {
      val hid = e.getIn.getHeader("hospitalNo",None,classOf[Option[String]])
      val nhs = e.getIn.getHeader("NHSNo",None,classOf[Option[String]])
      val visitName = e.getIn.getHeader("visitName",None,classOf[Option[String]])
      val visitId = visitName.flatMap(getVisit)

      e.getIn.setHeader("patientLinkedToHospitalNo", hid.flatMap(getPatientByHospitalNumber))
      e.getIn.setHeader("patientLinkedToNHSNo", nhs.flatMap(getPatientByNHSNumber))
      e.getIn.setHeader("visitId", visitId)
      e.getIn.setHeader("patientLinkedToVisit", visitId.flatMap(getPatientByVisitId))
    })
  } routeId "Set Extra Headers"

  def getHeader[T](e:Exchange,name:String):Option[T] = e.getIn.getHeader(name,None,classOf[Option[T]])

  updatePatientRoute ==> {
    choice {
      when(e => patientExists(e)) {
        process(e => patientUpdate(e))
      }
      when(e => !patientExists(e) && unknownPatientAction == Action.CREATE){
        process(e => patientNew(e))
        log(LoggingLevel.INFO, "updating headers")
        process(e =>{
          val hid = e.getIn.getHeader("hospitalNo",None,classOf[Option[String]])
          e.getIn.setHeader("patientLinkedToHospitalNo",hid.flatMap(getPatientByHospitalNumber))
        })
        log(LoggingLevel.INFO, "${header.patientLinkedToHospitalNo}")
      }

      when(e => !patientExists(e) && unknownPatientAction == Action.IGNORE){
        log(LoggingLevel.WARN, "Ignoring unknown Patient")
      }
      otherwise {
        throwException(new ADTUnknownPatientException("Unknown Patient"))
      }
    }
  } routeId "Create/Update Patient"

  updateVisitRoute ==> {
    choice {
    when(e => visitExists(e)) {
      process(e => visitUpdate(e))
    }
      when(e => !visitExists(e) && getHeader[String](e,"visitName").isDefined && unknownVisitAction == Action.CREATE){
        process(e => visitNew(e))
        log(LoggingLevel.INFO, "Updating visit headers for exchange: ${exchangeId}")
        process(e => {
           val visitName = e.getIn.getHeader("visitName",None,classOf[Option[String]])
          e.getIn.setHeader("visitId", visitName.flatMap(getVisit))
        })
      }
      when(e => !visitExists(e) && getHeader[String](e,"visitName").isDefined && unknownVisitAction == Action.IGNORE){
        log(LoggingLevel.WARN, "Ignoring unknown Visit")
      }
      otherwise {
        throwException(new ADTUnknownVisitException("Unknown visit"))
      }
    }
  } routeId "Create/Update Visit"

  A01Route ==> {
    when(e => visitExists(e)) throwException new ADTFieldException("Visit already exists")
    process(e => visitNew(e))
    -->(persistTimestamp)
  } routeId "A01"

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

  from(persistTimestamp) ==> {
    setHeader(RedisConstants.KEY,simple("${header.visitNameString}"))
    setHeader(RedisConstants.VALUE,simple("${header.timestamp}"))
    to("toRedis")
  }

  from(getTimestamp) ==> {
    setHeader(RedisConstants.COMMAND,"GET")
    setHeader(RedisConstants.KEY,simple("${header.visitNameString}"))
    enrich("fromRedis",new AggregateLastModTimestamp)
    log(LoggingLevel.INFO,"Last Mod Timestamp: ${header.lastModTimestamp} vs This message timestamp: ${header.timestamp}")
  }

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
      log(LoggingLevel.INFO, "visit exists about to discharge")
      process(e => patientDischarge(e))
    } otherwise {
      process(e => handleUnknownVisit(e => {
        log(LoggingLevel.INFO, "visit does not exist - will create visit before discharge")
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
      process(e => handleUnknownVisit(visitNew))
    }
  } routeId "A13"

  A05Route ==> {
    when(e => patientExists(e)) process(e => throw new ADTApplicationException("Patient with hospital number: " + e.in("hospitalNo") + " already exists"))
    process(e => patientNew(e))
  } routeId "A05"

  A28Route ==> {
    when(e => patientExists(e)) process(e => throw new ADTApplicationException("Patient with hospital number: " + e.in("hospitalNo") + " already exists"))
    process(e => patientNew(e))
    to(updateVisitRoute)
  } routeId "A28"

  A40Route ==> {
    when(e => !patientExists(e) || !mergeTargetExists(e)) throwException new ADTConsistencyException("Patients to merge did not exist")
    process(e => patientMerge(e))
    to(updateVisitRoute)
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

  def refersToCurrentAction(e:Exchange): Boolean = e.in("lastModTimestamp").toString <= e.in("timestamp").toString || e.in("lastModTimestamp") == ""
}

