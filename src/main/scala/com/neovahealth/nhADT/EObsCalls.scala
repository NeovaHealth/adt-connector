package com.neovahealth.nhADT

import com.neovahealth.nhADT.exceptions.ADTExceptions
import com.neovahealth.nhADT.utils.ConfigHelper
import com.tactix4.t4openerp.connector._
import com.tactix4.t4openerp.connector.transport.{OEDictionary, OEString, OEType}
import com.typesafe.scalalogging.slf4j.LazyLogging
import org.apache.camel.Exchange
import org.joda.time.DateTime

import scala.concurrent.Await
import scala.concurrent.duration._
import scalaz.Scalaz._
import scalaz._

/**
 * Created by max on 02/06/14.
 */
trait EObsCalls extends ADTProcessing with ADTExceptions with EObsQueries with LazyLogging {


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
    waitAndErr((hn |@| ohn)(
      session.callMethod("t4clinical.patient", "patientMerge", _, _)
    ))
  }

  def patientTransfer(implicit e: Exchange): Unit = {
    logger.info("Calling patientTransfer for patient: " + ~getHospitalNumber)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val wi = getWardIdentifier.toSuccess("Could not locate ward identifier").toValidationNel
    val bn = getBed.successNel[String]
    waitAndErr((hn |@| wi |@| bn)((i, w, b) => {
      val args: List[OEType] = List(i, w)
      val bed: Option[List[OEDictionary]] = b.map(z => List(OEDictionary("bed" -> OEString(z))))
      session.callMethod("t4clinical.patient", "patientTransfer", (args ++ ~bed): _*)
    }
    ))
  }

  def cancelPatientTransfer(implicit e: Exchange) = {
    logger.info("Calling cancelPatientTransfer for patient: " + ~getHospitalNumber)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val wi = getWardIdentifier.toSuccess("Could not locate ward identifier").toValidationNel
    val bn = getBed.successNel[String]
    waitAndErr((hn |@| wi |@| bn)((i, w, b) => {
      val args: List[OEType] = List(i, w)
      val bed: Option[List[OEDictionary]] = b.map(z => List(OEDictionary("bed" -> OEString(z))))
      session.callMethod("t4clinical.patient", "cancelTransfer", (args ++ ~bed): _*)
    }))
  }

  def visitNew(implicit e: Exchange): Unit = {
    logger.info("Calling visitNew for patient: " + ~getHospitalNumber)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val wi = getWardIdentifier.toSuccess("Could not locate ward identifier.").toValidationNel
    val vi = getVisitName.toSuccess("Could not locate visit identifier.").toValidationNel
    val sdt = (getVisitStartDate | new DateTime().toString(toDateTimeFormat)).successNel
    val om = getMapFromFields(ConfigHelper.optionalVisitFields).successNel

    waitAndErr((hn |@| wi |@| vi |@| sdt |@| om)((i, w, v, vs, o) => {
      session.callMethod("t4clinical.patient.visit", "visitNew", i, w, v, vs, OEDictionary(o.mapValues(OEString)))
    }))
  }

  def patientUpdate(implicit e: Exchange) = {
    logger.info("Calling patientUpdate for patient: " + ~getHospitalNumber)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val om = getMapFromFields(ConfigHelper.optionalPatientFields).successNel
    waitAndErr((hn |@| om)((h, o) => {
      session.callMethod("t4clinical.patient", "patientUpdate", h, OEDictionary(o.mapValues(OEString)))
    }))
  }

  def patientDischarge(implicit e: Exchange) = {
    logger.info("Calling patientDischarge for patient: " + ~getHospitalNumber)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val dd = (getDischargeDate | new DateTime().toString(toDateTimeFormat)).successNel
    waitAndErr((hn |@| dd)(session.callMethod("t4clinical.patient", "patientDischarge", _, _)))
  }

  def cancelPatientDischarge(implicit e: Exchange) = {
    logger.info("Calling cancelPatientDischarge for patient: " + ~getHospitalNumber)
    val vi = getVisitName.toSuccess("Could not locate visit identifier.").toValidationNel
    waitAndErr(vi.map(session.callMethod("t4clinical.patient.visit", "cancelDischarge", _)))
  }

  def patientNew(implicit e: Exchange) = {
    logger.info("Calling patientNew for patient: " + ~getHospitalNumber)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val om = getMapFromFields(ConfigHelper.optionalPatientFields).successNel
    waitAndErr((hn |@| om)((h, o) => {
      session.callMethod("t4clinical.patient", "patientNew", h, OEDictionary(o.mapValues(OEString)))
    }))
  }


  def cancelVisitNew(implicit e: Exchange) = {
    logger.info("Calling cancelVisitNew for patient: " + ~getHospitalNumber)
    val vi = getVisitName.toSuccess("Could not locate visit identifier.").toValidationNel
    waitAndErr(vi.map(session.callMethod("t4clinical.patient.visit", "cancelVisit", _)))
  }

  def visitUpdate(implicit e: Exchange) = {
    logger.info("Calling visitUpdate for patient: " + ~getHospitalNumber)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val wi = getWardIdentifier.toSuccess("Could not locate ward identifier.").toValidationNel
    val vi = getVisitName.toSuccess("Could not locate visit identifier.").toValidationNel
    val sdt = (getVisitStartDate | new DateTime().toString(toDateTimeFormat)).successNel
    val om = getMapFromFields(ConfigHelper.optionalVisitFields).successNel

    waitAndErr((hn |@| wi |@| vi |@| sdt |@| om)((h, w, v, vs, o) =>
      session.callMethod("t4clinical.patient.visit", "visitUpdate", h, w, v, vs, OEDictionary(o.mapValues(OEString)))))

  }
}
