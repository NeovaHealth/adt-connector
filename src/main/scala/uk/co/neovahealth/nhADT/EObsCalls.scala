package uk.co.neovahealth.nhADT

import com.tactix4.t4openerp.connector._
import com.tactix4.t4openerp.connector.transport.{OEArray, OEDictionary, OEString}
import com.typesafe.scalalogging.slf4j.{StrictLogging, LazyLogging}
import org.apache.camel.Exchange
import org.joda.time.DateTime
import uk.co.neovahealth.nhADT.exceptions.ADTExceptions
import uk.co.neovahealth.nhADT.utils.ConfigHelper

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.{implicitConversions, postfixOps}
import scalaz.Scalaz._
import scalaz._

/**
 * Created by max on 02/06/14.
 */
trait EObsCalls extends ADTProcessing with EObsQueries with StrictLogging{

  val Code = ".*(?i)Code$".r
  val Title = ".*(?i)Prefix$".r
  val Given = ".*(?i)GivenName".r
  val Family = ".*(?i)FamilyName".r

  def convertDoctorFields(x:Map[String,String]):Map[String,String] = x map {
    case (Code(), c)  => "code" -> c
    case (Given(), g) => "given_name" -> g
    case (Family(), f)=> "family_name" -> f
    case (Title(), t) => "title" -> t
    case z => z
  }

  def getMapFromFields(m: List[String])(implicit e: Exchange): Map[String, String] =
    m.map(f => getMessageValue(f).map(v => f -> v)).flatten.toMap

  def visitExists(implicit e: Exchange): Boolean =
    getVisitName(e).map(vid => getHeaderOrUpdate("visitId")(getVisit(vid))).isDefined

  def patientExists(implicit e: Exchange): Boolean =
    getHospitalNumber(e).map(hn => getHeaderOrUpdate("patientLinkedToHospitalNo")(getPatientByHospitalNumber(hn))).isDefined

  def mergeTargetExists(implicit e: Exchange): Boolean = {
    getOldHospitalNumber(e).flatMap(getPatientByHospitalNumber).isDefined
  }

  def waitAndErr(x: ValidationNel[String, OEResult[_]]): Unit = x.fold(
    errors => throw new ADTFieldException(errors.list.mkString(", ")),
    r => Await.result(r.run, timeOutMillis millis).fold(
      error => throw new ADTApplicationException(error),
      _ => ()
    )
  )

  def patientMerge(implicit e: Exchange): Unit = {
    logger.info("Calling patientMerge for patient: " + ~getHospitalNumber)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val ohn = getOldHospitalNumber.toSuccess("Could not locate old hospital number").toValidationNel
    waitAndErr((hn |@| ohn)( (h,o) =>
      session.callMethod("nh.eobs.api", "merge", h, "from_identifier" -> o)
    ))
  }

  def patientTransfer(implicit e: Exchange): Unit = {
    logger.info("Calling patientTransfer for patient: " + ~getHospitalNumber)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val ow = getMapFromFields(ConfigHelper.optionalPatientFields).successNel
    waitAndErr((hn |@| ow)( (h,o) =>
      session.callMethod("nh.eobs.api", "transfer", h, OEDictionary(o.mapValues(OEString)))
    ))
  }

  def cancelPatientTransfer(implicit e: Exchange) = {
    logger.info("Calling cancelPatientTransfer for patient: " + ~getHospitalNumber)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    waitAndErr(hn.map(session.callMethod("nh.eobs.api", "cancel_transfer", _)))
  }

  def visitNew(implicit e: Exchange): Unit = {
    logger.info("Calling visitNew for patient: " + ~getHospitalNumber)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val wi = getWardIdentifier.toSuccess("Could not locate ward identifier.").toValidationNel
    val vi = getVisitName.toSuccess("Could not locate visit identifier.").toValidationNel
    val sdt = (getMessageValue("visit_start_date_time") | new DateTime().toString(toDateTimeFormat)).successNel
    val cd = (getMapFromFields(ConfigHelper.consultingDoctorFields) ++ Map("type" -> "c")).successNel
    val rd = (getMapFromFields(ConfigHelper.referringDoctorFields) ++ Map("type" -> "r")).successNel

    waitAndErr((hn |@| wi |@| vi |@| sdt |@| cd.map(convertDoctorFields) |@| rd.map(convertDoctorFields))((i, w, v, vs, c, r) => {
      session.callMethod("nh.eobs.api", "admit", i, OEDictionary("location" -> w, "start_date" -> vs, "code" -> v, "doctors" -> OEArray(c,r)))
    }))
  }

  def patientUpdate(implicit e: Exchange) = {
    logger.info("Calling patientUpdate for patient: " + ~getHospitalNumber)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val om = getMapFromFields(ConfigHelper.optionalPatientFields).successNel
    waitAndErr((hn |@| om)( (h,o) => {
      session.callMethod("nh.eobs.api", "update", h, OEDictionary(o.mapValues(OEString)))
    }))
  }

  def patientDischarge(implicit e: Exchange) = {
    logger.info("Calling patientDischarge for patient: " + ~getHospitalNumber)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val dd = (getMessageValue("discharge_date") | new DateTime().toString(toDateTimeFormat)).successNel
    waitAndErr((hn |@| dd)((i,d) =>
      session.callMethod("nh.eobs.api", "discharge", i,"discharge_date" -> d)))
  }

  def cancelPatientDischarge(implicit e: Exchange) = {
    logger.info("calling cancelPatientDischarge for patient: " + ~getHospitalNumber)
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    waitAndErr(hn.map(session.callMethod("nh.eobs.api","cancel_discharge", _)))
  }

  def patientNew(implicit e: Exchange) = {
    logger.info("Calling patientNew for patient: " + ~getHospitalNumber)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val om = getMapFromFields(ConfigHelper.optionalPatientFields).successNel
    waitAndErr((hn |@| om)((h,o) => {
      session.callMethod("nh.eobs.api", "register", h, OEDictionary(o.mapValues(OEString)))
    }))
  }

  def cancelVisitNew(implicit e: Exchange) = {
    logger.info("calling cancelVisitNew for patient: " + ~getHospitalNumber)
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    waitAndErr(hn.map(session.callMethod("nh.eobs.api", "cancel_admit", _)))
  }

  def visitUpdate(implicit e: Exchange) = {
    logger.info("Calling visitUpdate for patient: " + ~getHospitalNumber)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val wi = getWardIdentifier.toSuccess("Could not locate ward identifier.").toValidationNel
    val vi = getVisitName.toSuccess("Could not locate visit identifier.").toValidationNel
    val sdt = (getMessageValue("visit_start_date_time") | new DateTime().toString(toDateTimeFormat)).successNel
    val cd = (getMapFromFields(ConfigHelper.consultingDoctorFields) ++ Map("type" -> "c")).successNel
    val rd = (getMapFromFields(ConfigHelper.referringDoctorFields) ++ Map("type" -> "r")).successNel

    waitAndErr((hn |@| wi |@| vi |@| sdt |@| cd.map(convertDoctorFields) |@| rd.map(convertDoctorFields))((i, w, v, vs, c, r) => {
      session.callMethod("nh.eobs.api", "admit_update", i, OEDictionary("location" -> w, "start_date" -> vs, "code" -> v, "doctors" -> OEArray(c, r)))
    }))

  }
}
