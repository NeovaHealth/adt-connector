package com.tactix4.t4ADT

import java.io.File
import java.util.concurrent.TimeUnit

import com.tactix4.t4ADT.utils.ConfigHelper
import com.tactix4.t4openerp.connector.OEConnector
import com.typesafe.config.{Config, ConfigValue, ConfigFactory}
import com.typesafe.scalalogging.Logging
import com.typesafe.scalalogging.slf4j.LazyLogging
import org.apache.camel.component.redis.RedisConstants
import org.apache.camel.model.IdempotentConsumerDefinition
import org.apache.camel.model.dataformat.HL7DataFormat
import org.apache.camel.processor.idempotent.IdempotentConsumer
import org.apache.camel.{LoggingLevel, Exchange}
import org.apache.camel.scala.dsl.builder.RouteBuilder

import ca.uhn.hl7v2.util.Terser
import ca.uhn.hl7v2.model.Message
import scalaz._
import Scalaz._

import org.joda.time.format.{DateTimeFormatter, DateTimeFormatterBuilder, DateTimeFormat}


import org.apache.camel.scala.dsl.SIdempotentConsumerDefinition
import com.tactix4.t4ADT.exceptions.ADTExceptions
import scala.util.matching.Regex
import scala.collection.JavaConversions._
import org.apache.camel.component.hl7.HL7.terser
import org.apache.camel.component.hl7.HL7.ack
import scala.concurrent.ExecutionContext.Implicits.global


class ADTInRoute() extends RouteBuilder with T4skrCalls with ADTErrorHandling with ADTProcessing with ADTExceptions with LazyLogging {

  implicit def idem2Sidem(i:IdempotentConsumerDefinition):SIdempotentConsumerDefinition = SIdempotentConsumerDefinition(i)(this)

  type VisitName = String

  val connector = new OEConnector(ConfigHelper.protocol, ConfigHelper.host, ConfigHelper.port)
    .startSession(ConfigHelper.username, ConfigHelper.password, ConfigHelper.database)

  val updateVisitRoute = "direct:updateOrCreateVisit"
  val updatePatientRoute = "direct:updateOrCreatePatient"
  val msgHistory = "msgHistory"
  val detectDuplicates = "direct:detectDuplicates"
  val detectUnsupportedMsg = "direct:detectUnsupportedMsgs"
  val detectUnsupportedWards = "direct:detectUnsupportedWards"
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
      log(LoggingLevel.ERROR,"I'm here")
      -->(setBasicHeaders)
      -->(detectDuplicates)
      -->(detectUnsupportedMsg)
      -->(detectUnsupportedWards)
      -->(setExtraHeaders)
      -->(detectIdConflict)
      -->(detectVisitConflict)
      //split on msgType
      choice {
        when(msgEquals("A08")) --> A08Route
        when(msgEquals("A31")) --> A31Route
        when(msgEquals("A05")) --> A05Route
        when(msgEquals("A28")) --> A28Route
        when(msgEquals("A01")) --> A01Route
        when(msgEquals("A11")) --> A11Route
        when(msgEquals("A03")) --> A03Route
        when(msgEquals("A13")) --> A13Route
        when(msgEquals("A02")) --> A02Route
        when(msgEquals("A12")) --> A12Route
        when(msgEquals("A40")) --> A40Route
        otherwise {
          throwException(new ADTUnsupportedMessageException("Unsupported msg type"))
        }
      }
      -->(msgHistory)
      transform(_.in[Message].generateACK())
      marshal(hl7)
    }
  } routeId "Main Route"

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
     e.getIn.setHeader("ignoreUnknownWards", ConfigHelper.ignoreUnknownWards)
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

  updatePatientRoute ==> {
    when(e => patientExists(e)) {
      process(e => patientUpdate(e))
    } otherwise {
      process(e => patientNew(e))
    }
  } routeId "Create/Update Patient"

  updateVisitRoute ==> {
    when(e => visitExists(e)) {
      process(e => visitUpdate(e))
    } otherwise {
      when(_.in("visitName") != None) process (e => visitNew(e))
    }
  } routeId "Create/Update Visit"


  A01Route ==> {
    when(e => visitExists(e)) throwException new ADTFieldException("Visit already exists")
    -->(updatePatientRoute)
    process(e => visitNew(e))
    -->(persistTimestamp)
  } routeId "A01"



  A11Route ==> {
    -->(updatePatientRoute)
    when(e => !visitExists(e)) {
      log(LoggingLevel.WARN, "Calling cancel admit on visit that doesn't exist - ignoring")
    } otherwise {
      process(e => cancelVisitNew(e))
    }
  } routeId "A11"

  A02Route ==> {
    -->(updatePatientRoute)
    when(e => !visitExists(e)) {
      process(e => visitNew(e))
    } otherwise {
      process(e => patientTransfer(e))
      to(updateVisitRoute)

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
    -->(updatePatientRoute)
    when(e => !visitExists(e)){
      log(LoggingLevel.WARN, "Calling cancelTransferPatient for visit that doesn't exist - ignoring")
    } otherwise {
      process(e => cancelPatientTransfer(e))
    }
    to(updateVisitRoute)
  } routeId "A12"

  A03Route ==> {
    -->(updatePatientRoute)
    when(e => !visitExists(e)) {
      log(LoggingLevel.WARN, "Calling discharge for a visit that doesn't exist - ignoring")
    } otherwise {
      process(e => patientDischarge(e))
      -->(persistTimestamp)
    }
  } routeId "A03"

  A13Route ==> {
    -->(updatePatientRoute)
    when(e => !visitExists(e)) {
      log(LoggingLevel.WARN, "Calling cancelDischarge on visit that doesn't exist - ignoring")
    } otherwise {
      process(e => cancelPatientDischarge(e))
    }
    to(updateVisitRoute)
  } routeId "A13"

  A05Route ==> {
    when(e => patientExists(e)) process(e => throw new ADTApplicationException("Patient with hospital number: " + e.in("hospitalNo") + " already exists"))
    process(e => patientNew(e))
    to(updateVisitRoute)
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
    when(e => (e.in("lastModTimestamp").toString >= e.in("timestamp").toString || e.in("lastModTimestamp") == "") && ! e.getIn.getHeader("hasDischargeDate",false, classOf[Boolean]) ){
      log(LoggingLevel.INFO,"Updating latest visit")
      filter(e => (for {
        codes <- getReasonCodes(e)
        mycode <- reasonCode(e)
      } yield codes contains mycode) | true ) {
        -->(updatePatientRoute)
        -->(updateVisitRoute)
      }
    } otherwise {
      log(LoggingLevel.INFO,"Ignoring Historical Message: ${header.timestamp}")
    }
  } routeId "A08"

  A31Route ==> {
    -->(updatePatientRoute)
  } routeId "A31"
}

