package com.tactix4.t4ADT

import org.apache.camel.model.dataformat.HL7DataFormat
import org.apache.camel.{LoggingLevel, Exchange}
import org.apache.camel.scala.dsl.SIdempotentConsumerDefinition
import org.apache.camel.scala.dsl.builder.RouteBuilder
import org.apache.camel.component.hl7.HL7._

import ca.uhn.hl7v2.util.Terser
import ca.uhn.hl7v2.model.Message

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.control.Exception._
import scalaz._
import Scalaz._

import org.joda.time.format.{DateTimeFormatter, DateTimeFormatterBuilder, DateTimeFormat}
import org.joda.time.DateTime

import com.tactix4.t4skr.{T4skrResult, T4skrConnector}
import com.tactix4.t4skr.core.{T4skrId, VisitId, HospitalNo}
import com.tactix4.t4openerp.connector.domain.Domain._
import com.tactix4.t4openerp.connector._

import com.typesafe.scalalogging.slf4j.Logging


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
                 val ignoreUnknownWards: Boolean) extends RouteBuilder with ADTProcessing with ADTErrorHandling with Logging {

  type VisitName = VisitId

  val connector = new T4skrConnector(protocol, host, port).startSession(username, password, database)

  val fromDateTimeFormat: DateTimeFormatter = new DateTimeFormatterBuilder().append(null, inputDateFormats.map(DateTimeFormat.forPattern(_).getParser).toArray).toFormatter
  val toDateTimeFormat = DateTimeFormat.forPattern(toDateFormat)
  val datesToParse = List("dob", "visit_start_date_time", "discharge_date")

  val triggerEventHeader = "CamelHL7TriggerEvent"

  val hl7 = new HL7DataFormat()
  hl7.setValidate(false)


  val updateVisitRoute = "seda:updateOrCreateVisit"
  val updatePatientRoute = "seda:updateOrCreatePatient"
  val msgHistory = "msgHistory"
  val convertMsg = "seda:convertMsg"

  val supportedMsgTypes = terserMap.keySet

  def convertMsgType(e:Exchange, msgType:String) = {
    val t = getTerser(e)
    t.map(terser =>{
      terser.set("MSH-9-2", msgType)
      e.getIn.setHeader("mappings", getCurrentMappings(terser,terserMap))
    })
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

  def getPatientByVisitName(vid: VisitName): Option[T4skrId] =
    Await.result(connector.oeSession.searchAndRead("t4clinical.patient.visit", "name" === vid, List("patient_id")).value, 2000 millis).fold(
      _ => None,
      ids => for {
        h <- ids.headOption
        oe <- h.get("patient_id")
        id <- oe.int
      } yield id
    )

  def getVisit(visitId: VisitName): Option[Int] =
    Await.result(connector.oeSession.search("t4clinical.patient.visit", "name" === visitId).value, 2000 millis).fold(
      _ => None,
      ids => ids.headOption
    )


  def visitExists(e: Exchange): Boolean = {
    val v = e.getIn.getHeader("visitId", classOf[Option[T4skrId]])
    v != null && v.isDefined
  }

  def patientExists(e: Exchange): Boolean = {
    val p = e.getIn.getHeader("patientLinkedToHospitalNo", classOf[Option[T4skrId]])
    p != null && p.isDefined
  }

  def getWardIdentifier(e: Exchange): Option[String] = {
    val originalMsgType = msgType(e)
    convertMsgType(e,"A01")
    val result = for {
      terser <- getTerser(e)
      mappings <- getMappings(e)
      r <- validateOptionalFields(List("ward_identifier"))(mappings,terser).get("ward_identifier")
    } yield  r
    originalMsgType.map(t => convertMsgType(e,t))
    result
  }

  def isSupportedWard(e:Exchange): Boolean = getWardIdentifier(e).fold(true)(w => wards contains w)

  def msgType(e: Exchange): Option[String] = checkForNull(e.getIn.getHeader(triggerEventHeader, classOf[String]))

  def getMessage(e: Exchange): Message = e.getIn.getHeader("origMessage", classOf[Message])

  def mergeTargetExists(e: Exchange): Boolean = {
    val m = e.getIn.getHeader("mappings", classOf[Map[String, String]])
    val t = e.getIn.getHeader("terser", classOf[Terser])
    getOldHospitalNumber(m, t).flatMap(getPatientByHospitalNumber).isDefined
  }

  def checkForNull[T](t : T) : Option[T] = (t != null) ? t.some | None

  def getTerser(e:Exchange): Option[Terser] = {
    checkForNull(e.getIn.getHeader("terser", classOf[Terser]))
  }
  def getMappings(e:Exchange) : Option[Map[String,String]] = {
    checkForNull(e.getIn.getHeader("mappings", classOf[Map[String, String]]))
  }
  
  def getVisitName(e:Exchange) : Option[VisitName] = {
    checkForNull(e.getIn.getHeader("visitName", classOf[Option[VisitName]])).flatten
  }

  def getPatientLinkedToHospitalNo(e:Exchange) : Option[T4skrId] ={
    checkForNull(e.getIn.getHeader("patientLinkedToHospitalNo", classOf[Option[T4skrId]])).flatten
  }
  def getPatientLinkedToNHSNo(e:Exchange) : Option[T4skrId] ={
    checkForNull(e.getIn.getHeader("patientLinkedToNHSNo", classOf[Option[T4skrId]])).flatten
  }

  "seda:idConflictCheck" ==> {
     when(e => {
        val p1 = getPatientLinkedToHospitalNo(e)
        val p2 = getPatientLinkedToNHSNo(e)
        p1.isDefined && p2.isDefined && p1 != p2
      }) {
        process( e => throw new ADTConsistencyException("Hospital number: " +e.in("hospitalNo") + " is linked to patient: "+ e.in("patientLinkedToHospitalNo") +
          " but NHS number: " + e.in("NHSNo") + "is linked to patient: " + e.in("patientLinkedToNHSNo")))
      }
  }

  "seda:visitConflictCheck" ==> {
     //check for conflict with visits
      when(e => {
        val vid = getVisitName(e)
        val pid = getPatientLinkedToHospitalNo(e) 
        val result = for {
          v <- vid
          p1 <- pid
          p2 <- getPatientByVisitName(v)
        } yield p1 != p2
        result | false
      }) {
        process(e => throw new ADTConsistencyException("Hospital number: " + e.in("hospitalNo") + " is linked to patient: " + e.in("patientLinkedToHospitalNo") + " " +
          " but visit: " + e.in("visitName") + " is linked to patient: " + e.in("patientLinkedToVisitId") + ""))
      }
  }

  def valueChanged[T](headerName: String, value:T, e:Exchange) = {
    e.getIn.getHeader(headerName) != value
  }

  "seda:updateHeaders" ==> {
    process(e => {
      val terser = getTerser(e)
      val mappings = terser.map(t => getCurrentMappings(t, terserMap))
      val hid = (for {
        t <- terser
        m <- mappings
      } yield getHospitalNumber(m, t)).getOrElse("")

      val nhs = for {
        t <- terser
        m <- mappings
        n <- getNHSNumber(m, t)
      } yield n

      val visitName = for {
        t <- terser
        m <- mappings
        n <- getVisitName(m, t)
      } yield n

      mappings.map(m => {
        if (valueChanged("mappings", m, e)) {
          e.getIn.setHeader("mappings", m)
        }
        if (valueChanged("hospitalNo", hid, e)) {
          logger.error("old hospitalNo: " + e.in("hospitalNo") + " - new hospitalNo: " + hid)
          e.getIn.setHeader("hospitalNo", hid)
          e.getIn.setHeader("patientLinkedToHospitalNo", ~getPatientByHospitalNumber(hid))
        }
        if (valueChanged("NHSNo", ~nhs, e)) {
          logger.error("old NHSNo: " + e.in("NHSNo") + " - new NHSNo: " + ~nhs)
          e.getIn.setHeader("NHSNo", ~nhs)
          e.getIn.setHeader("patientLinkedToNHSNo", ~nhs.flatMap(getPatientByNHSNumber))
        }
        if (valueChanged("visitName", ~visitName, e)) {
          logger.error("old visitName: " + e.in("visitName") + " - new VisitName: " + ~visitName)
          e.getIn.setHeader("visitName", ~visitName)
          e.getIn.setHeader("visitId", ~visitName.flatMap(getVisit))
          e.getIn.setHeader("patientLinkedToVisitName", ~visitName.flatMap(getPatientByVisitName))
        }
      })
    })

  }

  "seda:setHeaders" ==> {
    process(e => {
      val message = e.in[Message]
      val t = new Terser(message)
      val m = getCurrentMappings(t, terserMap)
      val hid = getHospitalNumber(m, t)
      val nhs = getNHSNumber(m, t)
      val visitName = getVisitName(m, t)

      e.getIn.setHeader("origMessage", message)
      e.getIn.setHeader("terser", t)
      e.getIn.setHeader("ignoreUnknownWards", ignoreUnknownWards)

      e.getIn.setHeader("mappings", m)
      e.getIn.setHeader("hospitalNo", hid)
      e.getIn.setHeader("patientLinkedToHospitalNo", ~getPatientByHospitalNumber(hid))
      e.getIn.setHeader("NHSNo", ~nhs)
      e.getIn.setHeader("patientLinkedToNHSNo", ~nhs.flatMap(getPatientByNHSNumber))
      e.getIn.setHeader("visitName", ~visitName)
      e.getIn.setHeader("visitId", ~visitName.flatMap(getVisit))
      e.getIn.setHeader("patientLinkedToVisitName", ~visitName.flatMap(getPatientByVisitName))

    })
  }

  "seda:detectDuplicates" ==>{
    when(_.getProperty(Exchange.DUPLICATE_MESSAGE)) throwException new ADTDuplicateMessageException("Duplicate message")
  }
  "seda:detectUnsupportedMsgs" ==> {
    when(e => !(supportedMsgTypes contains msgType(e).getOrElse(""))) throwException new ADTUnsupportedMessageException("Unsupported msg type")
  }
  "seda:detectUnsupportedWards" ==> {
    when(e => !isSupportedWard(e)) throwException new ADTUnsupportedWardException("Unsupported ward")
  }

  "hl7listener" ==> {
    unmarshal(hl7)
    SIdempotentConsumerDefinition(idempotentConsumer(_.in("CamelHL7MessageControl")) .messageIdRepositoryRef("messageIdRepo") .skipDuplicate(false) .removeOnFailure(false) )(this) {
      setHeader("msgBody",e => e.getIn.getBody.toString)
      -->("seda:detectDuplicates")
      -->("seda:detectUnsupportedMsgs")
      -->("seda:setHeaders")
      -->("seda:detectUnsupportedWards")
      -->("seda:idConflictCheck")
      -->("seda:visitConflictCheck")
      //split on msgType
      choice {
        when(e => ~msgType(e) == "A08" || ~msgType(e) == "A31") --> "seda:A08A31"
        when(e => ~msgType(e) == "A05" || ~msgType(e) == "A28") --> "seda:A05A28"
        when(e => ~msgType(e) == "A01") --> "seda:A01"
        when(e => ~msgType(e) == "A11") --> "seda:A11"
        when(e => ~msgType(e) == "A03") --> "seda:A03"
        when(e => ~msgType(e) == "A13") --> "seda:A13"
        when(e => ~msgType(e) == "A02") --> "seda:A02"
        when(e => ~msgType(e) == "A12") --> "seda:A12"
        when(e => ~msgType(e) == "A40") --> "seda:A40"
        otherwise{
          throwException(new ADTUnsupportedWardException("unsupported msg type"))
        }
      }
      -->(msgHistory)
      process(e => e.in = e.in[Message].generateACK())
    }
  }


  convertMsg ==> {
    when(_.in("convertTo") != null) {
      process(e => convertMsgType(e, e.getIn.getHeader("convertTo",classOf[String])))
      -->("seda:updateHeaders")
    } otherwise {
      throwException(new ADTApplicationException("convertMessage route must be called with 'convertTo' header set"))
    }

  }


  updatePatientRoute ==> {
    setHeader("origType",e => ~msgType(e) )
    when(e => patientExists(e)) {
      setHeader("convertTo","A31")
      -->(convertMsg)
      process(e => patientUpdate(e))

    } otherwise {
      setHeader("convertTo","A28")
      -->(convertMsg)
      process(e =>  patientNew(e))
    }
    setHeader("convertTo",header("origType"))
    -->(convertMsg)
  }

  updateVisitRoute ==> {
    when(e => visitExists(e)) {
      setHeader("convertTo","A31")
      -->(convertMsg)
      process(e => visitUpdate(e))
    } otherwise {
      filter(terser("PV1").isNotNull) --> "seda:visitNewRoute"
    }
  }

  "seda:visitNewRoute" ==> {
    setHeader("convertTo","A01")
    -->(convertMsg)
    process(e => visitNew(e))
  }

  "seda:cancelVisitNewRoute" ==> {
    setHeader("convertTo", "A11")
    -->(convertMsg)
    process(e => cancelVisitNew(e))
  }

  "seda:patientTransferRoute" ==> {
    setHeader("convertTo", "A02")
    -->(convertMsg)
    process(e => patientTransfer(e))
  }

  "seda:cancelPatientTransferRoute" ==> {
    setHeader("convertTo", "A12")
    -->(convertMsg)
    process(e => cancelPatientTransfer(e))
  }

  "seda:patientDischargeRoute" ==> {
    setHeader("convertTo", "A03")
    -->(convertMsg)
    process(e => patientDischarge(e))
  }

  "seda:cancelPatientDischargeRoute" ==> {
    setHeader("convertTo", "A13")
    -->(convertMsg)
    process(e => cancelPatientDischarge(e))
  }

  "seda:patientNewRoute" ==> {
    setHeader("convertTo", "A28")
    -->(convertMsg)
    process(e => patientNew(e))
  }

  "seda:patientMergeRoute" ==> {
    setHeader("convertTo", "A40")
    -->(convertMsg)
    process(e => patientMerge(e))
  }

  "seda:A01" ==> {
    when(e => visitExists(e)) throwException new ADTFieldException("Visit already exists")
    -->(updatePatientRoute)
    -->("seda:visitNewRoute")
  }


  "seda:A11" ==> {

    -->(updatePatientRoute)
    when(e => !visitExists(e)) {
      log(LoggingLevel.WARN, "Calling cancel admit on visit that doesn't exist - ignoring")
    } otherwise {
      -->("seda:cancelVisitNewRoute")
    }
  }

  "seda:A02" ==> {
    -->(updatePatientRoute)
    when(e => !visitExists(e)) {
      log(LoggingLevel.WARN, "Calling transferPatient for a visit that doesn't exist - ignoring")
    } otherwise {
      -->("seda:patientTransferRoute")
    }
    wireTap(updateVisitRoute)
  }

  "seda:A12" ==> {
    -->(updatePatientRoute)
    when(e => !visitExists(e)){
      log(LoggingLevel.WARN, "Calling cancelTransferPatient for visit that doesn't exist - ignoring")
    } otherwise {
      -->("seda:cancelPatientTransferRoute")
    }
    wireTap(updateVisitRoute)
  }

  "seda:A03" ==> {
    -->(updatePatientRoute)
    when(e => !visitExists(e)) {
      log(LoggingLevel.WARN, "Calling discharge for a visit that doesn't exist - ignoring")
    } otherwise {
      -->("seda:dischargePatientRoute")
    }
  }
  "seda:A13" ==> {
    -->(updatePatientRoute)
    when(e => !visitExists(e)) {
      log(LoggingLevel.WARN, "Calling cancelDischarge on patient that doesn't exist - ignoring")
    } otherwise {
      -->("seda:cancelDischargePatientRoute")
    }
    wireTap(updateVisitRoute)
  }

  "seda:A05A28" ==> {
    when(e => patientExists(e)) process(e => throw new ADTApplicationException("Patient with hospital number: " + e.in("hospitalNo") + " already exists"))
    -->("seda:patientNewRoute")
    wireTap(updateVisitRoute)

  }
  "seda:A40" ==> {
    when(e => !patientExists(e) || !mergeTargetExists(e)) throwException new ADTConsistencyException("Patients to merge did not exist")
    -->("seda:patientMerge")
    wireTap(updateVisitRoute)
  }

  "seda:A08A31" ==> {
    -->(updatePatientRoute)
    wireTap(updateVisitRoute)
  }


  def extract[T](f: Terser => Map[String, String] => T4skrResult[T])(implicit e: Exchange): Unit = {
    implicit val terser = getTerser(e)
    implicit val mappings = getMappings(e)

    val result =
      for {
        t <- T4skrResult(terser.toSuccess("Error no terser found"))
        m <- T4skrResult(mappings.toSuccess("Error no mappings found"))
        r <- f(t)(m)
      } yield  r

    Await.result(result.failMap(error => throw new ADTApplicationException(error)).value, timeOutMillis millis)
    ()
  }

  def patientMerge(implicit e: Exchange)= extract { terser => mappings =>
    val requiredFields = validateRequiredFields(List(hospitalNumber, oldHospitalNumber))(mappings, terser)
    connector.patientMerge(requiredFields(hospitalNumber), requiredFields(oldHospitalNumber))
  }

  def cancelPatientTransfer(implicit e: Exchange) = extract { implicit terser => implicit m =>
    val i = getHospitalNumber(m, implicitly)
    val w = validateRequiredFields(List("ward_identifier"))(m, implicitly)
    connector.patientTransfer(i, w("ward_identifier"))
    //    connector.cancelPatientTransfer(i,w("ward_identifier"))
  }

  def patientTransfer(implicit e: Exchange)= extract { implicit terser => implicit m =>
    val i = getHospitalNumber(m, implicitly)
    val w = validateRequiredFields(List("ward_identifier"))(m, implicitly)
    connector.patientTransfer(i, w("ward_identifier"))
  }

  def patientUpdate(implicit e: Exchange)= extract { implicit terser => implicit m =>
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
