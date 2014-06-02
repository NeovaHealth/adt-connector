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
import scalaz._
import Scalaz._

import org.joda.time.format.{DateTimeFormatter, DateTimeFormatterBuilder, DateTimeFormat}
import org.joda.time.DateTime

import com.tactix4.t4skr.{T4skrResult, T4skrConnector}
import com.tactix4.t4skr.core.{WardId, T4skrId, VisitId, HospitalNo}
import com.tactix4.t4openerp.connector.domain.Domain._
import com.tactix4.t4openerp.connector._

import com.typesafe.scalalogging.slf4j.Logging
import scala.collection.JavaConversions._


//TODO: Cacheing

class ADTApplicationException(msg: String, cause: Throwable = null) extends Exception(msg, cause)

class ADTConsistencyException(msg: String, cause: Throwable = null) extends Exception(msg, cause)

class ADTFieldException(msg: String, cause: Throwable = null) extends Exception(msg, cause)

class ADTUnsupportedMessageException(msg: String = null, cause: Throwable = null) extends Exception(msg, cause)

class ADTUnsupportedWardException(msg: String = null, cause: Throwable = null) extends Exception(msg, cause)

class ADTDuplicateMessageException(msg: String = null, cause: Throwable = null) extends Exception(msg, cause)

class ADTInRoute(val mappings: Map[String,String],
                 val msgTypeMappings: Map[String, Map[String, String]],
                 val protocol: String,
                 val host: String,
                 val port: Int,
                 val username: String,
                 val password: String,
                 val database: String,
                 val wards: Set[String],
                 val sexMap: Map[String, String],
                 val inputDateFormats: List[String],
                 val toDateFormat: String,
                 val datesToParse: List[String],
                 val timeOutMillis: Int,
                 val redeliveryDelay: Long,
                 val maximumRedeliveries: Int,
                 val ignoreUnknownWards: Boolean) extends RouteBuilder with ADTProcessing with ADTErrorHandling with Logging {

  type VisitName = VisitId
  val connector = new T4skrConnector(protocol, host, port).startSession(username, password, database)
  val fromDateTimeFormat: DateTimeFormatter = new DateTimeFormatterBuilder().append(null, inputDateFormats.map(DateTimeFormat.forPattern(_).getParser).toArray).toFormatter
  val toDateTimeFormat = DateTimeFormat.forPattern(toDateFormat)
  val triggerEventHeader = "CamelHL7TriggerEvent"
  val hl7 = new HL7DataFormat()
  hl7.setValidate(false)

  val updateVisitRoute = "direct:updateOrCreateVisit"
  val updatePatientRoute = "direct:updateOrCreatePatient"
  val msgHistory = "msgHistory"

  val supportedMsgTypes = msgTypeMappings.keySet

  val optionalPatientFields = List("patient_identifier", "given_name",  "family_name",  "middle_names",  "title", "sex", "dob")
  val optionalVisitFields = List("consultingDoctorCode", "consultingDoctorPrefix", "consultingDoctorGivenName", "consultingDoctorFamilyName","referringDoctorCode","referringDoctorPrefix", "referringDoctorGivenName", "referringDoctorFamilyName","service_code")


  def getPatientByHospitalNumber(hospitalNumber: HospitalNo): Option[T4skrId] =
    Await.result(connector.oeSession.search("t4clinical.patient", "other_identifier" === hospitalNumber).value, 2000 millis).fold(
      _ => None,
      ids => {
        println(ids)
        ids.headOption
      }
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
        a <- oe.array
        h <- a.headOption
        id <- h.int
        _ = println(id)
      } yield id
    )

  def getVisit(visitId: VisitName): Option[Int] =
    Await.result(connector.oeSession.search("t4clinical.patient.visit", "name" === visitId).value, 2000 millis).fold(
      _ => None,
      ids => ids.headOption
    )


  def getMapFromFields(m:List[String])(implicit t:Terser) ={
    m.map(f => getMessageValue(f).map(v => f -> v)).flatten.toMap
  }

  def visitExists(e: Exchange): Boolean = {
    e.getIn.getHeader("visit_id") != null
//    getVisitName(getTerser(e)).flatMap(getVisit).isDefined
  }

  def patientExists(e: Exchange): Boolean = {
    val p = e.getIn.getHeader("patientLinkedToHospitalNo", classOf[Option[T4skrId]])
    p != null && p.isDefined
  }

  def isSupportedWard(e:Exchange): Boolean = getWardIdentifier(getTerser(e)).fold(true)(w => wards contains w)

  def msgType(e: Exchange): Option[String] = checkForNull(e.getIn.getHeader(triggerEventHeader, classOf[String]))

  def getMessage(e: Exchange): Message = e.getIn.getHeader("origMessage", classOf[Message])

  def mergeTargetExists(e: Exchange): Boolean = {
    getOldHospitalNumber(getTerser(e)).flatMap(getPatientByHospitalNumber).isDefined
  }

  def checkForNull[T](t : T) : Option[T] = (t != null) ? t.some | None

  def getTerser(e:Exchange):Terser =  e.getIn.getHeader("terser", classOf[Terser])

  def getPatientLinkedToHospitalNo(e:Exchange) : Option[T4skrId] ={
    checkForNull(e.getIn.getHeader("patientLinkedToHospitalNo", classOf[Option[T4skrId]])).flatten
  }
  def getPatientLinkedToVisitName(e:Exchange) : Option[T4skrId] ={
    checkForNull(e.getIn.getHeader("patientLinkedToVisitName", classOf[Option[T4skrId]])).flatten
  }
  def getPatientLinkedToNHSNo(e:Exchange) : Option[T4skrId] ={
    checkForNull(e.getIn.getHeader("patientLinkedToNHSNo", classOf[Option[T4skrId]])).flatten
  }

  "direct:idConflictCheck" ==> {
     when(e => {
        val p1 = getPatientLinkedToHospitalNo(e)
        val p2 = getPatientLinkedToNHSNo(e)
        p1.isDefined && p2.isDefined && p1 != p2
      }) {
        process( e => throw new ADTConsistencyException("Hospital number: " +e.in("hospitalNo") + " is linked to patient: "+ e.in("patientLinkedToHospitalNo") +
          " but NHS number: " + e.in("NHSNo") + "is linked to patient: " + e.in("patientLinkedToNHSNo")))
      }
  }

  "direct:visitConflictCheck" ==> {
     //check for conflict with visits
      when(e => {
        val pv = getPatientLinkedToVisitName(e)
        val ph = getPatientLinkedToHospitalNo(e)
        pv.isDefined && pv != ph
      }){
        process( e => throw new ADTConsistencyException("Hospital number: " + e.in("hospitalNo") + " is linked to patient: " + e.in("patientLinkedToHospitalNo") + " " +
          " but visit: " + e.in("visitName") + " is linked to patient: " + e.in("patientLinkedToVisitName") + ""))
      }
  }

  "direct:setHeaders" ==> {
    process(e => {
      val message = e.in[Message]
      val t = new Terser(message)
      val hid = getHospitalNumber(t)
      val nhs = getNHSNumber(t)
      val visitName = getVisitName(t)

      e.getIn.setHeader("origMessage", message)
      e.getIn.setHeader("terser", t)
      e.getIn.setHeader("hospitalNo", hid)
      e.getIn.setHeader("NHSNo", nhs)
      e.getIn.setHeader("visitName", visitName)
      e.getIn.setHeader("ignoreUnknownWards", ignoreUnknownWards)

      e.getIn.setHeader("patientLinkedToHospitalNo", hid.flatMap(getPatientByHospitalNumber))
      e.getIn.setHeader("patientLinkedToNHSNo", nhs.flatMap(getPatientByNHSNumber))
      e.getIn.setHeader("patientLinkedToVisitName", visitName.flatMap(getPatientByVisitName))
      e.getIn.setHeader("visitId", visitName.flatMap(getVisit))

      val h = e.getIn.getHeaders.toMap
      println("hospital No: " + h("hospitalNo"))
      println("NHS No: " + h("NHSNo"))
      println("Visit Name: " + h("visitName"))
      println("patient t4skrId from hospitalNo:  " + h("patientLinkedToHospitalNo"))
      println("patient t4skrId from NHS No:  " + h("patientLinkedToNHSNo"))
      println("patient t4skrId from Visit Name:  " + h("patientLinkedToVisitName"))

    })
  }

  "direct:detectDuplicates" ==>{
    when(_.getProperty(Exchange.DUPLICATE_MESSAGE)) throwException new ADTDuplicateMessageException("Duplicate message")
  }
  "direct:detectUnsupportedMsgs" ==> {
    when(e => !(supportedMsgTypes contains msgType(e).getOrElse(""))) throwException new ADTUnsupportedMessageException("Unsupported msg type")
  }
  "direct:detectUnsupportedWards" ==> {
    when(e => !isSupportedWard(e)) throwException new ADTUnsupportedWardException("Unsupported ward")
  }

  "hl7listener" ==> {
    unmarshal(hl7)
    SIdempotentConsumerDefinition(idempotentConsumer(_.in("CamelHL7MessageControl")) .messageIdRepositoryRef("messageIdRepo") .skipDuplicate(false) .removeOnFailure(false) )(this) {
      setHeader("msgBody",e => e.getIn.getBody.toString)
      -->("direct:detectDuplicates")
      -->("direct:detectUnsupportedMsgs")
      -->("direct:setHeaders")
      -->("direct:detectUnsupportedWards")
      -->("direct:idConflictCheck")
      -->("direct:visitConflictCheck")
      //split on msgType
      choice {
        when(e => ~msgType(e) == "A08" || ~msgType(e) == "A31") --> "direct:A08A31"
        when(e => ~msgType(e) == "A05" || ~msgType(e) == "A28") --> "direct:A05A28"
        when(e => ~msgType(e) == "A01") --> "direct:A01"
        when(e => ~msgType(e) == "A11") --> "direct:A11"
        when(e => ~msgType(e) == "A03") --> "direct:A03"
        when(e => ~msgType(e) == "A13") --> "direct:A13"
        when(e => ~msgType(e) == "A02") --> "direct:A02"
        when(e => ~msgType(e) == "A12") --> "direct:A12"
        when(e => ~msgType(e) == "A40") --> "direct:A40"
        otherwise{
          throwException(new ADTUnsupportedWardException("Unsupported msg type"))
        }
      }
      -->(msgHistory)
      process(e => e.in = e.in[Message].generateACK())
    }
  }


  updatePatientRoute ==> {
    when(e => patientExists(e)) {
      process(e => patientUpdate(e))
    } otherwise {
      process(e => patientNew(e))
    }
  }

  updateVisitRoute ==> {
    when(e => visitExists(e)) {
      process(e => visitUpdate(e))
    } otherwise {
      filter(terser("PV1").isNotNull) process (e => visitNew(e))
    }
  }


  "direct:A01" ==> {
    when(e => visitExists(e)) throwException new ADTFieldException("Visit already exists")
    -->(updatePatientRoute)
    process(e => visitNew(e))
  }


  "direct:A11" ==> {
    -->(updatePatientRoute)
    when(e => !visitExists(e)) {
      log(LoggingLevel.WARN, "Calling cancel admit on visit that doesn't exist - ignoring")
    } otherwise {
      process(e => cancelVisitNew(e))
    }
  }

  "direct:A02" ==> {
    -->(updatePatientRoute)
    when(e => !visitExists(e)) {
      log(LoggingLevel.WARN, "Calling transferPatient for a visit that doesn't exist - ignoring")
    } otherwise {
      process(e => patientTransfer(e))
    }
    wireTap(updateVisitRoute)
  }

  "direct:A12" ==> {
    -->(updatePatientRoute)
    when(e => !visitExists(e)){
      log(LoggingLevel.WARN, "Calling cancelTransferPatient for visit that doesn't exist - ignoring")
    } otherwise {
      process(e => cancelPatientTransfer(e))
    }
    wireTap(updateVisitRoute)
  }

  "direct:A03" ==> {
    -->(updatePatientRoute)
    when(e => !visitExists(e)) {
      log(LoggingLevel.WARN, "Calling discharge for a visit that doesn't exist - ignoring")
    } otherwise {
      process(e => patientDischarge(e))
    }
  }
  "direct:A13" ==> {
    -->(updatePatientRoute)
    when(e => !visitExists(e)) {
      log(LoggingLevel.WARN, "Calling cancelDischarge on visit that doesn't exist - ignoring")
    } otherwise {
      process(e => cancelPatientDischarge(e))
    }
    wireTap(updateVisitRoute)
  }

  "direct:A05A28" ==> {
    when(e => patientExists(e)) process(e => throw new ADTApplicationException("Patient with hospital number: " + e.in("hospitalNo") + " already exists"))
    process(e => patientNew(e))
    wireTap(updateVisitRoute)
  }
  "direct:A40" ==> {
    when(e => !patientExists(e) || !mergeTargetExists(e)) throwException new ADTConsistencyException("Patients to merge did not exist")
    process(e => patientMerge(e))
    wireTap(updateVisitRoute)
  }

  "direct:A08A31" ==> {
    -->(updatePatientRoute)
    -->(updateVisitRoute)
  }

  def waitAndErr(x:ValidationNel[String,T4skrResult[_]]) = x.fold(
      errors => throw new ADTFieldException(errors.shows),
      r => Await.result(r.value,2000 millis).fold(
        error => throw new ADTApplicationException(error),
        _ => ()
      )
    )

  def patientMerge(e:Exchange) = {
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val ohn = getOldHospitalNumber.toSuccess("Could not locate old hospital number").toValidationNel
    waitAndErr((hn |@| ohn)(connector.patientMerge))
  }
  def patientTransfer(e:Exchange) = {
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val wi = getWardIdentifier.toSuccess("Could not locate ward identifier").toValidationNel
    waitAndErr((hn |@| wi)(connector.patientTransfer))
  }

  def cancelPatientTransfer(e: Exchange) = {
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val wi = getWardIdentifier.toSuccess("Could not locate ward identifier").toValidationNel
    waitAndErr((hn |@| wi)(connector.patientTransferCancel))
  }

  def visitNew(e:Exchange) : Unit = {
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val wi = getWardIdentifier.toSuccess("Could not locate ward identifier.").toValidationNel
    val vi = getVisitName.toSuccess("Could not locate visit identifier.").toValidationNel
    val sdt = getMessageValue("visit_start_date_time").toSuccess("Could not locate visit start date time.").toValidationNel
    val om = getMapFromFields(optionalVisitFields).successNel

    waitAndErr((hn |@| wi |@| vi |@| sdt |@| om)(connector.visitNew))
  }

  def patientUpdate(e:Exchange) = {
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val om = getMapFromFields(optionalPatientFields).successNel
    waitAndErr((hn |@| om)(connector.patientUpdate))
  }

  def patientDischarge(e: Exchange) = {
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val dd = (getMessageValue("discharge_date") | new DateTime().toString(toDateTimeFormat)).successNel
    waitAndErr((hn |@| dd)(connector.patientDischarge))
  }

  def cancelPatientDischarge(e: Exchange) = {
    implicit val t = getTerser(e)
    val vi = getVisitName.toSuccess("Could not locate visit identifier.").toValidationNel
    waitAndErr(vi.map(connector.patientDischargeCancel))
  }
  def patientNew(e: Exchange) = {
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val om = getMapFromFields(optionalPatientFields).successNel
    waitAndErr((hn |@| om)(connector.patientNew))
  }

  def cancelVisitNew(e: Exchange) = {
    implicit val t = getTerser(e)
    val vi = getVisitName.toSuccess("Could not locate visit identifier.").toValidationNel
    waitAndErr(vi.map(connector.visitCancel))
  }

  def visitUpdate(e: Exchange) = {
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val wi = getWardIdentifier.toSuccess("Could not locate ward identifier.").toValidationNel
    val vi = getVisitName.toSuccess("Could not locate visit identifier.").toValidationNel
    val sdt = getMessageValue("visit_start_date_time").toSuccess("Could not locate visit start date time.").toValidationNel
    val om = getMapFromFields(optionalVisitFields).successNel

    waitAndErr((hn |@| wi |@| vi |@| sdt |@| om)(connector.visitUpdate))
  }}
