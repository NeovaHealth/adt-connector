package com.tactix4.t4ADT

import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

import com.typesafe.config.{Config, ConfigValue, ConfigFactory}
import org.apache.camel.model.IdempotentConsumerDefinition
import org.apache.camel.model.dataformat.HL7DataFormat
import org.apache.camel.component.hl7.HL7.ack
import org.apache.camel.spi.IdempotentRepository
import org.apache.camel.{InOut, ExchangePattern, LoggingLevel, Exchange}
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
import scala.collection.JavaConversions._


class ADTInRoute() extends RouteBuilder with T4skrCalls with ADTErrorHandling with ADTProcessing with ADTExceptions with Logging {

  type VisitName = VisitId

  val f = {
   val t =new File("etc/tactix4/com.tactix4.t4ADT.conf")
    if (!t.canRead) new File("src/test/resources/com.tactix4.t4ADT.conf")
    else t
  }


  val config: Config = ConfigFactory.parseFile(f)

  val protocol: String = config.getString("openERP.protocol")
  val host: String = config.getString("openERP.hostname")
  val port: Int = config.getInt("openERP.port")
  val username: String = config.getString("openERP.username")
  val password: String = config.getString("openERP.password")
  val database: String = config.getString("openERP.database")
  val wards : List[Regex] = config.getStringList("misc.ward_names").map(_.r).toList
  val sexMap: Map[String, String] = config.getObject("ADT_mappings.sex_map").toMap.mapValues(_.unwrapped().asInstanceOf[String])
  val inputDateFormats: List[String] = config.getStringList("misc.valid_date_formats").toList
  val toDateFormat: String = config.getString("openERP.to_date_format")
  val datesToParse: Set[String] = config.getStringList("ADT_mappings.dates_to_parse").toSet
  val timeOutMillis: Long = config.getDuration("camel_redelivery.time_out",TimeUnit.MILLISECONDS)
  val redeliveryDelay: Long = config.getDuration("camel_redelivery.delay",TimeUnit.MILLISECONDS)
  val maximumRedeliveries: Int = config.getInt("camel_redelivery.maximum_redeliveries")
  val ignoreUnknownWards: Boolean = config.getBoolean("misc.ignore_unknown_wards")
  val bedRegex:Regex = config.getString("misc.bed_regex").r
  val ratePer2Seconds:Int = config.getInt("misc.rate_per_2_seconds")
  val supportedMsgTypes = config.getStringList("misc.supported_msg_types")

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

  val incomingQueue = "rabbitMQEndpointIn"
  val forwardQueue = "rabbitMQEndpointOnward"

  val msgType = (e:Exchange) => e.getIn.getHeader(triggerEventHeader, classOf[String])

  //  val iRepo = getContext.getEndpoint("messageIdRepo",classOf[IdempotentRepository[_]])

  def reasonCode(implicit e:Exchange) = e.getIn.getHeader("eventReasonCode",classOf[Option[String]])

  "hl7listener" --> "activemq:queue:in"

    from("activemq:queue:in") ==> {
      throttle(ratePer2Seconds per (2 seconds)) {
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
    when(e => !(supportedMsgTypes contains e.in(triggerEventHeader).toString)) throwException new ADTUnsupportedMessageException("Unsupported msg type")
  } routeId "Detect Unsupported Msg"

  detectUnsupportedWards ==> {
    when(e => !isSupportedWard(e)) throwException new ADTUnsupportedWardException("Unsupported ward")
  } routeId "Detect Unsupported Wards"

  detectConflicts ==> {
     when(e => {
        val p1 = getPatientLinkedToHospitalNo(e)
        val p2 = getPatientLinkedToNHSNo(e)
        p1.isDefined && p2.isDefined && p1 != p2 || getPatientLinkedToVisit(e) != p1
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
     e.getIn.setHeader("ignoreUnknownWards", ignoreUnknownWards)
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
    when(e => patientExists(e)) throwException new ADTApplicationException("Patient with hospital number: ".in("hospitalNo") + " already exists"))
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

