package com.tactix4.t4ADT

import ca.uhn.hl7v2.model.Message
import ca.uhn.hl7v2.util.Terser
import com.tactix4.t4ADT.exceptions.ADTExceptions
import com.tactix4.t4ADT.utils.ConfigHelper
import com.tactix4.t4skr.T4skrConnector
import com.tactix4.t4skr.core._
import com.typesafe.config.Config
import com.typesafe.scalalogging.slf4j.Logging
import org.apache.camel.component.hl7.HL7.ack
import org.apache.camel.model.dataformat.HL7DataFormat
import org.apache.camel.scala.dsl.SIdempotentConsumerDefinition
import org.apache.camel.scala.dsl.builder.RouteBuilder
import org.apache.camel.{Exchange, LoggingLevel}
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter, DateTimeFormatterBuilder}
import scala.util.matching.Regex
import scalaz._
import Scalaz._

class ADTInRoute() extends RouteBuilder with T4skrCalls with ADTErrorHandling with ADTProcessing with ADTExceptions with Logging {

  type VisitName = VisitId

  override val wards: List[Regex] = ConfigHelper.wards
  override val config: Config = ConfigHelper.config
  override val maximumRedeliveries: Int = ConfigHelper.maximumRedeliveries
  override val redeliveryDelay: Long = ConfigHelper.redeliveryDelay
  override val bedRegex: Regex = ConfigHelper.bedRegex
  override val datesToParse: Set[String] = ConfigHelper.datesToParse
  override val sexMap: Map[String, String] = ConfigHelper.sexMap

  val connector = new T4skrConnector(ConfigHelper.protocol, ConfigHelper.host, ConfigHelper.port)
    .startSession(ConfigHelper.username, ConfigHelper.password, ConfigHelper.database)

  val fromDateTimeFormat: DateTimeFormatter = new DateTimeFormatterBuilder()
    .append(null, ConfigHelper.inputDateFormats.map(DateTimeFormat.forPattern(_).getParser).toArray).toFormatter

  val toDateTimeFormat = DateTimeFormat.forPattern(ConfigHelper.toDateFormat)

  val hl7 = new HL7DataFormat()
  hl7.setValidate(false)

  val updateVisitRoute = "direct:updateOrCreateVisit"
  val updatePatientRoute = "direct:updateOrCreatePatient"
  val msgHistory = "msgHistory"
  val detectDuplicates = "direct:detectDuplicates"
  val detectUnsupportedMsg = "direct:detectUnsupportedMsgs"
  val detectUnsupportedWards = "direct:detectUnsupportedWards"
  val setBasicHeaders = "direct:setBasicHeaders"
  val setExtraHeaders = "direct:setExtraHeaders"
  val detectConflicts = "direct:conflictCheck"


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


  //ROUTES:

  "hl7listener" --> "activemq:queue:in"

  "activemq:queue:in" ==> {
    throttle(ConfigHelper.ratePer2Seconds per (2 seconds)) {
      unmarshal(hl7)
      SIdempotentConsumerDefinition(idempotentConsumer(_.in("CamelHL7MessageControl")).messageIdRepositoryRef("messageIdRepo").skipDuplicate(false).removeOnFailure(false))(this) {
        -->(setBasicHeaders)
        -->(detectDuplicates)
        -->(detectUnsupportedMsg)
        -->(detectUnsupportedWards)
        -->(setExtraHeaders)
        -->(detectConflicts)
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
          otherwise throwException new ADTUnsupportedMessageException("Unsupported msg type")

        }
        wireTap(msgHistory)
        transform(ack())

      }
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

  detectConflicts ==> {
     when(e => {
        val p1 = getPatientLinkedToHospitalNo(e)
        val p2 = getPatientLinkedToNHSNo(e)
        p1.isDefined && p2.isDefined && p1 != p2 || (getPatientLinkedToVisit(e).map(v => Some(v) != p1) | false)
      }) {
        process( e => throw new ADTConsistencyException("Hospital number linked to: " + e.in("patientLinkedToHospitalNo") +
          "\nNHS number linked to: " + e.in("patientLinkedToNHSNo") + "\nVisitID linked to:" +  e.in("patientLinkedToVisit")))
      }
  } routeId "Detect Id Conflict"

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
    filter(e => (for {
                  codes <- getReasonCodes(e)
                  mycode <- reasonCode(e)
                } yield codes contains mycode) | true ) {
      wireTap(updateVisitRoute)
    }
  } routeId "A08A31"
}

