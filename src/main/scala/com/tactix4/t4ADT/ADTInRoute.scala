package com.tactix4.t4ADT

import org.apache.camel.scala.dsl.builder.RouteBuilder
import org.apache.camel.model.dataformat.HL7DataFormat
import org.apache.camel.component.hl7.HL7.ack

import ca.uhn.hl7v2.util.Terser
import ca.uhn.hl7v2.model.Message

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.Exception._
import scalaz._
import Scalaz._

import org.joda.time.format.{DateTimeFormatter, DateTimeFormatterBuilder, DateTimeFormat}

import com.tactix4.t4skr.{T4skrResult, T4skrConnector}
import com.tactix4.t4ADT.utils.Instrumented
import org.apache.camel.{LoggingLevel, Exchange}
import org.apache.camel.scala.dsl.SIdempotentConsumerDefinition

import com.tactix4.t4skr.core.{T4skrId, PatientId, VisitId, HospitalNo}
import org.apache.camel.component.hl7.HL7.terser
import org.joda.time.DateTime
import ca.uhn.hl7v2.{AcknowledgmentCode, HL7Exception}
import com.tactix4.t4openerp.connector.domain.Domain._
import com.tactix4.t4openerp.connector._
import com.typesafe.scalalogging.slf4j.Logging
import org.apache.camel.component.hl7.AckCode.AA


/**
 * A Camel Route for receiving ADT messages over an MLLP connector
 * via the mina2 component, validating them, then calling the associated
 * t4skrConnetor methods and returning an appropriate ack
 * Note: we block on the async t4skrConnector methods because the mina connection
 * is synchronous
 */

class ADTApplicationException(msg: String, cause: Throwable = null) extends Exception(msg, cause)

class ADTConsistencyException(msg: String, cause: Throwable = null) extends Exception(msg, cause)

class ADTFieldException(msg: String, cause: Throwable = null) extends Exception(msg, cause)

class ADTUnsupportedMessageException(msg: String = null, cause: Throwable = null) extends Exception(msg, cause)

class ADTUnsupportedWardException(msg: String = null, cause: Throwable = null) extends Exception(msg, cause)

class ADTDuplicateMessageException(msg: String = null, cause: Throwable = null) extends Exception(msg, cause)

class ADTInRoute(implicit val terserMap: Map[String, Map[String, String]],
                 val protocol: String,
                 val host: String,
                 val port: Int,
                 val username: String,
                 val password: String,
                 val database: String,
                 val inputDateFormats: List[String],
                 val wards: Set[String],
                 val sexMap: Map[String, String],
                 val toDateFormat: String,
                 val timeOutMillis: Int,
                 val redeliveryDelay: Long,
                 val maximumRedeliveries: Int,
                 val ignoreUnknownWards: Boolean) extends RouteBuilder with ADTProcessing with ADTErrorHandling with Instrumented with Logging {


  val connector = new T4skrConnector(protocol, host, port).startSession(username, password, database)

  val fromDateTimeFormat: DateTimeFormatter = new DateTimeFormatterBuilder().append(null, inputDateFormats.map(DateTimeFormat.forPattern(_).getParser).toArray).toFormatter
  val toDateTimeFormat = DateTimeFormat.forPattern(toDateFormat)
  val datesToParse = List("dob", "visit_start_date_time", "discharge_date")

  val triggerEventHeader = "CamelHL7TriggerEvent"
  val hl7 = new HL7DataFormat()
  hl7.setValidate(false)


  val supportedMsgTypes = Set("A01", "A02", "A03", "A11", "A12", "A13", "A05", "A08", "A28", "A31", "A40")

  def convertMsgType(e:Exchange, msgType:String) = {
     getTerser(e).set("MSH-9-2", msgType)
    e.getIn.setHeader("mappings", getMappings(getTerser(e),terserMap))
  }

  def getPatientByHospitalNumber(hospitalNumber: HospitalNo): Option[T4skrId] =
    Await.result(connector.oeSession.search("t4clinical.patient", "other_identifier" === hospitalNumber).value, 2000 millis).fold(
      _ => None,
      ids => ids.headOption
    )

  def getPatientByNHSNumber(nhsNumber: String): Option[T4skrId] =
    Await.result(connector.oeSession.search("t4clinical.patient", "patient_identifier" === nhsNumber).value, 2000 millis).fold(
      _ => None,
      ids => ids.headOption
    )

  def getPatientByVisitIdentifier(vid: VisitId): Option[T4skrId] =
    Await.result(connector.oeSession.searchAndRead("t4clinical.patient.visit", "patient_identifier" === vid, List("patient_id")).value, 2000 millis).fold(
      _ => None,
      ids => for {
        h <- ids.headOption
        oe <- h.get("patient_id")
        id <- oe.int
      } yield id
    )

  def getVisit(visitId: VisitId): Option[Int] =
    Await.result(connector.oeSession.search("t4clinical.patient.visit", "name" === visitId).value, 2000 millis).fold(
      _ => None,
      ids => ids.headOption
    )


  def visitExists(e: Exchange): Boolean = e.getIn.getHeader("visitId", classOf[Option[Int]]).isDefined

  def patientExists(e: Exchange): Boolean = {
    e.getIn.getHeader("patientLinkedToHospitalNo", classOf[Option[T4skrId]]).isDefined
  }


  def getWardIdentifier(e: Exchange): Option[String] = {
    val originalMsgType = msgType(e)
    convertMsgType(e,"A01")
    val t = getTerser(e)
    val m = e.getIn.getHeader("mappings",classOf[Map[String,String]])
    val result = validateOptionalFields(List("ward_identifier"))(m,t).get("ward_identifier")
    convertMsgType(e,originalMsgType)
    result
  }

  def isSupportedWard(e:Exchange): Boolean = getWardIdentifier(e).fold(true)(w => wards contains w)

  def msgType(e: Exchange): String = e.getIn.getHeader(triggerEventHeader, classOf[String])

  def getMessage(e: Exchange): Message = e.getIn.getHeader("origMessage", classOf[Message])

  def mergeTargetExists(e: Exchange): Boolean = {
    val m = e.getIn.getHeader("mappings", classOf[Map[String, String]])
    val t = e.getIn.getHeader("terser", classOf[Terser])
    getOldHospitalNumber(m, t).flatMap(getPatientByHospitalNumber).isDefined
  }
  def getTerser(e:Exchange): Terser =  e.getIn.getHeader("terser",classOf[Terser])

  "hl7listener" ==> {
    unmarshal(hl7)
    SIdempotentConsumerDefinition(idempotentConsumer(_.in("CamelHL7MessageControl")) .messageIdRepositoryRef("messageIdRepo") .skipDuplicate(false) .removeOnFailure(false) )(this) {
      //detect duplicates
      when(_.getProperty(Exchange.DUPLICATE_MESSAGE)) throwException new ADTDuplicateMessageException("Duplicate message")
      //detect unsupported messages before any other processing
      when(e => !(supportedMsgTypes contains msgType(e))) throwException new ADTUnsupportedMessageException("Unsupported msg type ${in.header.CamelHL7TriggerEvent}")

      //cache some values
      process(e => {
        val message = e.in[Message]
        val t = new Terser(message)
        val m = getMappings(t, terserMap)
        val hid = getHospitalNumber(m, t)
        val nhs = getNHSNumber(m, t)
        val visitName = getVisitName(m, t)

        e.getIn.setHeader("msgBody", e.getIn.getBody.toString)
        e.getIn.setHeader("origMessage", message)
        e.getIn.setHeader("terser", t)
        e.getIn.setHeader("mappings", m)
        e.getIn.setHeader("hospitalNo", hid)
        e.getIn.setHeader("NHSNo", nhs)
        e.getIn.setHeader("visitName", visitName)
        e.getIn.setHeader("visitId", visitName.flatMap(getVisit))
        e.getIn.setHeader("patientLinkedToHospitalNo", getPatientByHospitalNumber(hid))
        e.getIn.setHeader("patientLinkedToNHSNo", nhs.flatMap(getPatientByNHSNumber))
        e.getIn.setHeader("patientLinkedToVisitId", visitName.flatMap(getPatientByVisitIdentifier))
        e.getIn.setHeader("ignoreUnknownWards", ignoreUnknownWards)

      })
      //detect unsupported wards
      when(e => !isSupportedWard(e)) throwException new ADTUnsupportedWardException("Unsupported ward")

      //check for conflict with IDs
      when(e => {
        val p1 = e.getIn.getHeader("patientLinkedToHospitalNo",classOf[Option[T4skrId]])
        val p2 = e.getIn.getHeader("patientLinkedToNHSNo",classOf[Option[T4skrId]])
        p1.isDefined && p2.isDefined && p1 != p2
      }) {
        throwException(new ADTConsistencyException("Hospital number: ${in.header.hospitalNo} is linked to patient: ${in.header.patientLinkedToHospitalNo}" +
          " but NHS number: ${in.header.NHSNo} is linked to patient: ${in.header.patientLinkedToNHSNo}"))
      }

//      //check for conflict with visits
//      when(e => {
//        val vid = e.getIn.getHeader("visitId",classOf[Option[T4skrId]])
//        val pid = e.getIn.getHeader("patientLinkedToHospitalNo",classOf[Option[T4skrId]])
//        val result = for {
//          v <- vid
//          p1 <- pid
//          p2 <- getPatientByVisitIdentifier(v)
//        } yield p1 != p2
//        result | false
//      }) {
//        throwException(new ADTConsistencyException("Hospital number: ${in.header.hospitalNo} is linked to patient: ${in.header.patientLinkedToHospitalNo} " +
//          " but visit: ${in.header.visitName} is linked to patient: ${in.header.patientLinkedToVisitId}"))
//      }
      //split on msgType
      choice {
        when(e => msgType(e) == "A08" || msgType(e) == "A31") --> "direct:A08A31"
        when(e => msgType(e) == "A05" || msgType(e) == "A28") --> "direct:A05A28"
        when(e => msgType(e) == "A01") --> "direct:A01"
        when(e => msgType(e) == "A11") --> "direct:A11"
        when(e => msgType(e) == "A03") --> "direct:A03"
        when(e => msgType(e) == "A13") --> "direct:A13"
        when(e => msgType(e) == "A02") --> "direct:A02"
        when(e => msgType(e) == "A12") --> "direct:A12"
        when(e => msgType(e) == "A40") --> "direct:A40"
      }
    }
  }

  "direct:createOrUpdatePatient" ==> {
    when(e => patientExists(e)) {
      process(e => {
        convertMsgType(e,"A31")
        patientUpdate(e)
      })
    } otherwise {
      process(e => {
        convertMsgType(e,"A28")
        patientNew(e)
      })
    }
  }



  "direct:A01" ==> {
    when(e => visitExists(e)) throwException new ADTFieldException("Visit ${in.header.visitName} already exists")
    -->("direct:createOrUpdatePatient")
    process(e => {
      convertMsgType(e,"A01")
      e.in = visitNew(e)
    })
    -->("msgHistory")
  }


  "direct:A11" ==> {

    -->("direct:createOrUpdatePatient")
    when(e => !visitExists(e)) {
      log(LoggingLevel.WARN, "Calling cancel admit on visit that doesn't exist - ignoring")
      process(e => e.in = e.in[Message].generateACK())
    } otherwise {
      process(e => e.in = cancelVisitNew(e))
    }
    -->("msgHistory")
  }

  "direct:A02" ==> {
    -->("direct:createOrUpdatePatient")
    when(e => !visitExists(e)) {
      log(LoggingLevel.WARN, "Calling transferPatient for a visit that doesn't exist - ignoring")
      process(e => e.in = e.in[Message].generateACK())
    } otherwise {
      process(e => e.in = patientTransfer(e))
    }
    -->("msgHistory")
  }

  "direct:A12" ==> {
    -->("direct:createOrUpdatePatient")
    when(e => !visitExists(e)){
      log(LoggingLevel.WARN, "Calling cancelTransferPatient for visit that doesn't exist - ignoring")
      process(e => e.in = e.in[Message].generateACK())
    } otherwise {
      process(e => e.in = cancelPatientTransfer(e))
    }
    -->("msgHistory")
  }

  "direct:A03" ==> {
    -->("direct:createOrUpdatePatient")
    when(e => !visitExists(e)) {
      log(LoggingLevel.WARN, "Calling discharge on patient that doesn't exist - ignoring")
      process(e => e.in = e.in[Message].generateACK())
    } otherwise {
      process(e => e.in = patientDischarge(e))
    }
    -->("msgHistory")
  }
  "direct:A13" ==> {
    -->("direct:createOrUpdatePatient")
    when(e => !visitExists(e)) {
      log(LoggingLevel.WARN, "Calling cancelDischarge on patient that doesn't exist - ignoring")
      process(e => e.in = e.in[Message].generateACK())
    } otherwise {
      process(e => e.in = cancelPatientDischarge(e))
    }
    -->("msgHistory")
  }

  "direct:A05A28" ==> {
    when(e => patientExists(e)) throwException new ADTApplicationException("Patient with hospital number: ${in.header.hospitalNo} already exists")
    process(e => e.in = patientNew(e))
    wireTap("seda:visitExistsRoute")
    to("msgHistory")
  }
  "direct:A40" ==> {
    when(e => !patientExists(e) || !mergeTargetExists(e)) throwException new ADTConsistencyException("Patients to merge did not exist")
    process(e => e.in  = patientMerge(e))
    wireTap("seda:visitExistsRoute")
    to("msgHistory)")
  }

  "direct:A08A31" ==> {
    -->("direct:createOrUpdatePatient")
    wireTap("seda:visitExistsRoute")
    -->("msgHistory")
  }

  "seda:visitExistsRoute" ==> {
    choice {
      when(e => visitExists(e)) {
        process(e => visitUpdate(e))
      } otherwise {
        process(e => visitNew(e))
      }
    }
  }



  def extract(f: Terser => Map[String, String] => T4skrResult[_])(implicit e: Exchange): Message = {
    implicit val terser = e.getIn.getHeader("terser", classOf[Terser])
    implicit val mappings = e.getIn.getHeader("mappings", classOf[Map[String, String]])

    val result = allCatch either Await.result(f(terser)(mappings) value, timeOutMillis millis)

    result.left.map((error: Throwable) => throw new ADTApplicationException(error.getMessage, error))

    e.in[Message].generateACK()
  }

  def patientMerge(implicit e: Exchange): Message = extract { terser => mappings =>
    val requiredFields = validateRequiredFields(List(hospitalNumber, oldHospitalNumber))(mappings, terser)
    connector.patientMerge(requiredFields(hospitalNumber), requiredFields(oldHospitalNumber))
  }

  def cancelPatientTransfer(implicit e: Exchange): Message = extract { implicit terser => implicit m =>
    val i = getHospitalNumber(m, implicitly)
    val w = validateRequiredFields(List("ward_identifier"))(m, implicitly)
    connector.patientTransfer(i, w("ward_identifier"))
    //    connector.cancelPatientTransfer(i,w("ward_identifier"))
  }

  def patientTransfer(implicit e: Exchange): Message = extract { implicit terser => implicit m =>
    val i = getHospitalNumber(m, implicitly)
    val w = validateRequiredFields(List("ward_identifier"))(m, implicitly)
    connector.patientTransfer(i, w("ward_identifier"))
  }

  def patientUpdate(implicit e: Exchange): Message = extract { implicit terser => implicit m =>
    val i = getHospitalNumber(m, implicitly)
    val o = validateAllOptionalFields(Map(hospitalNumber -> i))(m, implicitly)
    connector.patientUpdate(i, o)
  }

  def patientDischarge(implicit e: Exchange) = extract { implicit t => implicit m =>
    val i = getHospitalNumber(m, implicitly)
    val r = validateOptionalFields(List("discharge_date"))(m, implicitly)
    val o = r.get("discharge_date") getOrElse new DateTime().toString(toDateTimeFormat)
    connector.patientDischarge(i, o)
  }

  def cancelPatientDischarge(implicit e: Exchange) = extract { implicit t => implicit m =>
    val r = validateRequiredFields(List("visit_identifier"))(m, implicitly)
    connector.patientDischargeCancel(r("visit_identifier"))
  }

  def patientNew(implicit e: Exchange) = extract { implicit t => implicit m =>
    val i = getHospitalNumber(m, implicitly)
    val o = validateAllOptionalFields(Map(hospitalNumber -> i))(m, implicitly)
    connector.patientNew(i, o)
  }

  def cancelVisitNew(implicit e: Exchange) = extract { implicit t => implicit m =>
    val r = validateRequiredFields(List("visit_identifier"))(m, implicitly)
    connector.visitCancel(r("visit_identifier"))
  }

  def visitNew(implicit e: Exchange) = extract { implicit t => implicit m =>
    val requiredFields = validateRequiredFields(List("ward_identifier", "visit_identifier", "visit_start_date_time"))(m, t)
    val o = validateAllOptionalFields(requiredFields)(m, implicitly)
    connector.visitNew(getHospitalNumber(m, t), requiredFields("ward_identifier"), requiredFields("visit_identifier"), requiredFields("visit_start_date_time"), o)
  }


  def visitUpdate(implicit e: Exchange) = extract { implicit t => implicit m =>
    val requiredFields = validateRequiredFields(List("ward_identifier", "visit_identifier", "visit_start_date_time"))(m, implicitly)
    val o = validateAllOptionalFields(requiredFields)(m, implicitly)
    connector.visitUpdate(getHospitalNumber(m, t), requiredFields("ward_identifier"), requiredFields("visit_identifier"), requiredFields("visit_start_date_time"), o)
  }

}
