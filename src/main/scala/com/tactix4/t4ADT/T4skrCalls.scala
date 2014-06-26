package com.tactix4.t4ADT


import com.tactix4.t4skr.{T4skrResult, T4skrSession}


import scala.concurrent.Await
import scala.concurrent.duration._


import com.tactix4.t4skr.core._
import com.tactix4.t4openerp.connector.domain.Domain._
import com.tactix4.t4openerp.connector._
import scalaz._
import Scalaz._


import org.apache.camel.Exchange
import ca.uhn.hl7v2.util.Terser
import org.joda.time.DateTime
import com.tactix4.t4ADT.exceptions.ADTExceptions
import com.typesafe.scalalogging.slf4j.Logging
import scala.util.matching.Regex

/**
 * Created by max on 02/06/14.
 */
trait T4skrCalls extends ADTProcessing with ADTExceptions with T4skrQueries with Logging {

  val wards:List[Regex]
  val triggerEventHeader = "CamelHL7TriggerEvent"


  val optionalPatientFields = List("patient_identifier", "given_name",  "family_name",  "middle_names",  "title", "sex", "dob")
  val optionalVisitFields = List("consultingDoctorCode", "consultingDoctorPrefix", "consultingDoctorGivenName", "consultingDoctorFamilyName","referringDoctorCode","referringDoctorPrefix", "referringDoctorGivenName", "referringDoctorFamilyName","service_code", "bed")


  def getMapFromFields(m:List[String])(implicit t:Terser) ={
    m.map(f => getMessageValue(f).map(v => f -> v)).flatten.toMap
  }

  def visitExists(e: Exchange): Boolean = {
    val v = e.getIn.getHeader("visitId",classOf[Option[String]])
    v != null && v.isDefined
  }

  def patientExists(e: Exchange): Boolean = {
    val p = e.getIn.getHeader("patientLinkedToHospitalNo", classOf[Option[T4skrId]])
    p != null && p.isDefined
  }

  def mergeTargetExists(e: Exchange): Boolean = {
    getOldHospitalNumber(getTerser(e)).flatMap(getPatientByHospitalNumber).isDefined
  }

  def isSupportedWard(e:Exchange): Boolean = getWardIdentifier(getTerser(e)).fold(true)(w =>wards.exists(_.findFirstIn(w).isDefined))

  def msgEquals(t: String)(e: Exchange): Boolean = t == e.getIn.getHeader(triggerEventHeader, classOf[String])

  def checkForNull[T](t : T) : Option[T] = (t != null) ? t.some | None

  def getTerser(e:Exchange):Terser = e.getIn.getHeader("terser", classOf[Terser])

  def getPatientLinkedToHospitalNo(e:Exchange) : Option[T4skrId] =
    checkForNull(e.getIn.getHeader("patientLinkedToHospitalNo", classOf[Option[T4skrId]])).flatten

  def getPatientLinkedToVisit(e:Exchange) : Option[T4skrId] =
    checkForNull(e.getIn.getHeader("patientLinkedToVisit", classOf[Option[T4skrId]])).flatten

  def getPatientLinkedToNHSNo(e:Exchange) : Option[T4skrId] =
    checkForNull(e.getIn.getHeader("patientLinkedToNHSNo", classOf[Option[T4skrId]])).flatten

  def waitAndErr(x:ValidationNel[String,T4skrResult[_]]) = x.fold(
      errors => throw new ADTFieldException(errors.shows),
      r => Await.result(r.value,2000 millis).fold(
        error => throw new ADTApplicationException(error),
        _ => ()
      )
    )

  def patientMerge(e:Exchange) = {
    logger.info("Calling patientMerge")
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val ohn = getOldHospitalNumber.toSuccess("Could not locate old hospital number").toValidationNel
    waitAndErr((hn |@| ohn)(connector.patientMerge))
  }

  def patientTransfer(e:Exchange) = {
    logger.info("Calling patientTransfer")
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val wi = getWardIdentifier.toSuccess("Could not locate ward identifier").toValidationNel
    val bn = getMessageValue("bed").successNel[String]
    waitAndErr((hn |@| wi |@| bn)(connector.patientTransfer))
  }

  def cancelPatientTransfer(e: Exchange) = {
    logger.info("Calling cancelPatientTransfer")
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val wi = getWardIdentifier.toSuccess("Could not locate ward identifier").toValidationNel
    val bn = getMessageValue("bed").successNel[String]
    waitAndErr((hn |@| wi |@| bn)(connector.patientTransferCancel))
  }

  def visitNew(e:Exchange) : Unit = {
    logger.info("Calling visitNew")
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val wi = getWardIdentifier.toSuccess("Could not locate ward identifier.").toValidationNel
    val vi = getVisitName.toSuccess("Could not locate visit identifier.").toValidationNel
    val sdt = (getMessageValue("visit_start_date_time") |  new DateTime().toString(toDateTimeFormat)).successNel
    val om = getMapFromFields(optionalVisitFields).successNel

    waitAndErr((hn |@| wi |@| vi |@| sdt |@| om)(connector.visitNew))
  }

  def patientUpdate(e:Exchange) = {
    logger.info("calling patientUpdate")
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val om = getMapFromFields(optionalPatientFields).successNel
    waitAndErr((hn |@| om)(connector.patientUpdate))
  }

  def patientDischarge(e: Exchange) = {
    logger.info("calling patientDischarge")
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val dd = (getMessageValue("discharge_date") | new DateTime().toString(toDateTimeFormat)).successNel
    waitAndErr((hn |@| dd)(connector.patientDischarge))
  }

  def cancelPatientDischarge(e: Exchange) = {
    logger.info("calling cancelPatientDischarge")
    implicit val t = getTerser(e)
    val vi = getVisitName.toSuccess("Could not locate visit identifier.").toValidationNel
    waitAndErr(vi.map(connector.patientDischargeCancel))
  }
  def patientNew(e: Exchange) = {
    logger.info("calling patientNew")
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val om = getMapFromFields(optionalPatientFields).successNel
    waitAndErr((hn |@| om)(connector.patientNew))
  }

  def cancelVisitNew(e: Exchange) = {
    logger.info("calling cancelVisitNew")
    implicit val t = getTerser(e)
    val vi = getVisitName.toSuccess("Could not locate visit identifier.").toValidationNel
    waitAndErr(vi.map(connector.visitCancel))
  }

  def visitUpdate(e: Exchange) = {
    logger.info("calling visitUpdate")
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val wi = getWardIdentifier.toSuccess("Could not locate ward identifier.").toValidationNel
    val vi = getVisitName.toSuccess("Could not locate visit identifier.").toValidationNel
    val sdt = (getMessageValue("visit_start_date_time") | new DateTime().toString(toDateTimeFormat)).successNel
    val om = getMapFromFields(optionalVisitFields).successNel

    waitAndErr((hn |@| wi |@| vi |@| sdt |@| om)(connector.visitUpdate))
  }
}
