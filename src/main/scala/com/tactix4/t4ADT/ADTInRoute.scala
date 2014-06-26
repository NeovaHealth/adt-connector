package com.tactix4.t4ADT

import org.apache.camel.model.dataformat.HL7DataFormat
import org.apache.camel.{LoggingLevel, Exchange}
import org.apache.camel.scala.dsl.builder.RouteBuilder

import ca.uhn.hl7v2.util.Terser
import ca.uhn.hl7v2.model.Message
import scalaz._
import Scalaz._

import org.joda.time.format.{DateTimeFormatter, DateTimeFormatterBuilder, DateTimeFormat}

import com.tactix4.t4skr.T4skrConnector
import com.tactix4.t4skr.core._

import com.typesafe.scalalogging.slf4j.Logging
import org.apache.camel.scala.dsl.SIdempotentConsumerDefinition
import com.tactix4.t4ADT.exceptions.ADTExceptions
import scala.util.matching.Regex


class ADTInRoute(val mappings: Map[String,String],
                 val msgTypeMappings: Map[String, Map[String, String]],
                 val protocol: String,
                 val host: String,
                 val port: Int,
                 val username: String,
                 val password: String,
                 val database: String,
                 val wards: List[Regex],
                 val sexMap: Map[String, String],
                 val inputDateFormats: List[String],
                 val toDateFormat: String,
                 val datesToParse: List[String],
                 val timeOutMillis: Int,
                 val redeliveryDelay: Long,
                 val maximumRedeliveries: Int,
                 val ignoreUnknownWards: Boolean,
                 val bedRegex:Regex,
                val ratePer2Seconds:Int) extends RouteBuilder with T4skrCalls with ADTErrorHandling with ADTProcessing with ADTExceptions with Logging {

  type VisitName = VisitId
  val connector = new T4skrConnector(protocol, host, port).startSession(username, password, database)
  val fromDateTimeFormat: DateTimeFormatter = new DateTimeFormatterBuilder().append(null, inputDateFormats.map(DateTimeFormat.forPattern(_).getParser).toArray).toFormatter
  val toDateTimeFormat = DateTimeFormat.forPattern(toDateFormat)
  val hl7 = new HL7DataFormat()
  hl7.setValidate(false)

  val updateVisitRoute = "direct:updateOrCreateVisit"
  val updatePatientRoute = "direct:updateOrCreatePatient"
  val msgHistory = "msgHistory"
  val detectDuplicates = "direct:detectDuplicates"
  val detectUnsupportedMsg = "direct:detectUnsupportedMsgs"
  val detectUnsupportedWards = "direct:detectUnsupportedWards"
  val setHeaders = "direct:setHeaders"
  val detectIdConflict = "direct:idConflictCheck"
  val detectVisitConflict = "direct:visitConflictCheck"

  val supportedMsgTypes = msgTypeMappings.keySet

  val A08A31Route = "direct:A0831"
  val A05A28Route = "direct:A05A28"
  val A08Route = A08A31Route
  val A31Route = A08A31Route
  val A05Route = A05A28Route
  val A28Route = A05A28Route

  val A01Route = "direct:A01"
  val A02Route = "direct:A02"
  val A03Route = "direct:A03"
  val A11Route = "direct:A11"
  val A12Route = "direct:A12"
  val A13Route = "direct:A13"
  val A40Route = "direct:A40"

  val incomingQueue = "seda:incoming"

  "hl7listener" --> incomingQueue routeId("incoming")

  incomingQueue ==> {
    throttle(ratePer2Seconds per(2 seconds)) {
      unmarshal(hl7)
      SIdempotentConsumerDefinition(idempotentConsumer(_.in("CamelHL7MessageControl")).messageIdRepositoryRef("messageIdRepo").skipDuplicate(false).removeOnFailure(false))(this) {
        setHeader("msgBody", e => e.getIn.getBody.toString)
        -->(detectDuplicates)
        -->(detectUnsupportedMsg)
        -->(detectUnsupportedWards)
        -->(setHeaders)
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
            throwException(new ADTUnsupportedWardException("Unsupported msg type"))
          }
        }
        -->(msgHistory)
        process(e => e.in = e.in[Message].generateACK())
      }
    } routeId "Main Route"
  }

  detectDuplicates ==>{
    when(_.getProperty(Exchange.DUPLICATE_MESSAGE)) throwException new ADTDuplicateMessageException("Duplicate message")
  } routeId "Detect Duplicates"

  detectUnsupportedMsg ==> {
    when(e => !(supportedMsgTypes contains e.in(triggerEventHeader).toString)) throwException new ADTUnsupportedMessageException("Unsupported msg type")
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

  setHeaders ==> {
    process(e => {
      val message = e.in[Message]
      val t = new Terser(message)
      val hid = getHospitalNumber(t)
      val nhs = getNHSNumber(t)
      val visitName = getVisitName(t)
      val visitId = visitName.flatMap(getVisit)

      e.getIn.setHeader("origMessage", message)
      e.getIn.setHeader("terser", t)
      e.getIn.setHeader("hospitalNo", hid)
      e.getIn.setHeader("NHSNo", nhs)
      e.getIn.setHeader("visitName", visitName)
      e.getIn.setHeader("ignoreUnknownWards", ignoreUnknownWards)

      e.getIn.setHeader("patientLinkedToHospitalNo", hid.flatMap(getPatientByHospitalNumber))
      e.getIn.setHeader("patientLinkedToNHSNo", nhs.flatMap(getPatientByNHSNumber))
      e.getIn.setHeader("visitId", visitId)
      e.getIn.setHeader("patientLinkedToVisit", visitId.flatMap(getPatientByVisitId))

    })
  } routeId "Set Headers"

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
      log(LoggingLevel.WARN, "Calling transferPatient for a visit that doesn't exist - ignoring")
    } otherwise {
      process(e => patientTransfer(e))
    }
    wireTap(updateVisitRoute)
  } routeId "A02"

  A12Route ==> {
    -->(updatePatientRoute)
    when(e => !visitExists(e)){
      log(LoggingLevel.WARN, "Calling cancelTransferPatient for visit that doesn't exist - ignoring")
    } otherwise {
      process(e => cancelPatientTransfer(e))
    }
    wireTap(updateVisitRoute)
  } routeId "A12"

  A03Route ==> {
    -->(updatePatientRoute)
    when(e => !visitExists(e)) {
      log(LoggingLevel.WARN, "Calling discharge for a visit that doesn't exist - ignoring")
    } otherwise {
      process(e => patientDischarge(e))
    }
  } routeId "A03"

  A13Route ==> {
    -->(updatePatientRoute)
    when(e => !visitExists(e)) {
      log(LoggingLevel.WARN, "Calling cancelDischarge on visit that doesn't exist - ignoring")
    } otherwise {
      process(e => cancelPatientDischarge(e))
    }
    wireTap(updateVisitRoute)
  } routeId "A13"

  A05A28Route ==> {
    when(e => patientExists(e)) process(e => throw new ADTApplicationException("Patient with hospital number: " + e.in("hospitalNo") + " already exists"))
    process(e => patientNew(e))
    wireTap(updateVisitRoute)
  } routeId "A05A28"

  A40Route ==> {
    when(e => !patientExists(e) || !mergeTargetExists(e)) throwException new ADTConsistencyException("Patients to merge did not exist")
    process(e => patientMerge(e))
    wireTap(updateVisitRoute)
  } routeId "A40"

  A08A31Route ==> {
    -->(updatePatientRoute)
    wireTap(updateVisitRoute)
  } routeId "A08A31"

}
