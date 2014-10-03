package com.tactix4.t4ADT


import com.tactix4.t4ADT.utils.ConfigHelper
import com.tactix4.t4openerp.connector.transport.{OEType, OEString, OEDictionary}


import scala.concurrent.Await
import scala.concurrent.duration._


import com.tactix4.t4openerp.connector.domain.Domain._
import com.tactix4.t4openerp.connector._
import scalaz._
import Scalaz._


import org.apache.camel.Exchange
import ca.uhn.hl7v2.util.Terser
import org.joda.time.DateTime
import com.tactix4.t4ADT.exceptions.ADTExceptions
import com.typesafe.scalalogging.slf4j.{LazyLogging}
import scala.util.matching.Regex
import scala.collection.JavaConversions._

/**
 * Created by max on 02/06/14.
 */
trait T4skrCalls extends ADTProcessing with ADTExceptions with T4skrQueries with LazyLogging {

  val triggerEventHeader = "CamelHL7TriggerEvent"

  def getMapFromFields(m:List[String])(implicit t:Terser) ={
    m.map(f => getMessageValue(f).map(v => f -> v)).flatten.toMap
  }

  def visitExists(e: Exchange): Boolean = {
    e.getIn.getHeader("visitId",None,classOf[Option[String]]).isDefined
  }

  def patientExists(e: Exchange): Boolean = {
    e.getIn.getHeader("patientLinkedToHospitalNo", None, classOf[Option[Int]]).isDefined
  }

  def mergeTargetExists(e: Exchange): Boolean = {
    getOldHospitalNumber(getTerser(e)).flatMap(getPatientByHospitalNumber).isDefined
  }

  def isSupportedWard(e:Exchange): Boolean = getWardIdentifier(getTerser(e)).fold(true)(w =>ConfigHelper.wards.exists(_.findFirstIn(w).isDefined))

  val msgType = (e:Exchange) => e.getIn.getHeader(triggerEventHeader, classOf[String])

  def msgEquals(t: String)(e: Exchange): Boolean = t == msgType(e)


  def checkForNull[T](t : T) : Option[T] = (t != null) ? t.some | None

  def getTerser(e:Exchange):Terser = e.getIn.getHeader("terser", classOf[Terser])

  def getPatientLinkedToHospitalNo(e:Exchange) : Option[Int] =
    checkForNull(e.getIn.getHeader("patientLinkedToHospitalNo", classOf[Option[Int]])).flatten

  def getPatientLinkedToVisit(e:Exchange) : Option[Int] =
    checkForNull(e.getIn.getHeader("patientLinkedToVisit", classOf[Option[Int]])).flatten

  def getPatientLinkedToNHSNo(e:Exchange) : Option[Int] =
    checkForNull(e.getIn.getHeader("patientLinkedToNHSNo", classOf[Option[Int]])).flatten

  def waitAndErr(x:ValidationNel[String,OEResult[_]]): Unit = x.fold(
      errors => throw new ADTFieldException(errors.shows),
      r => Await.result(r.run,timeOutMillis millis).fold(
        error => throw new ADTApplicationException(error),
        _ => ()
      )
    )

  def patientMerge(e:Exchange): Unit = {
    logger.info("Calling patientMerge")
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val ohn = getOldHospitalNumber.toSuccess("Could not locate old hospital number").toValidationNel
    waitAndErr((hn |@| ohn)(
      connector.callMethod("t4clinical.patient", "patientMerge", _, _)
    ))
  }

  def patientTransfer(e:Exchange): Unit = {
    logger.info("Calling patientTransfer")
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val wi = getWardIdentifier.toSuccess("Could not locate ward identifier").toValidationNel
    val bn = getMessageValue("bed").successNel[String]
    waitAndErr((hn |@| wi |@| bn)((i,w,b)  => {
      val args: List[OEType] = List(i, w)
      val bed:Option[List[OEDictionary]] = b.map(z => List(OEDictionary("bed" -> OEString(z))))
      connector.callMethod("t4clinical.patient", "patientTransfer", (args ++ ~bed): _*)
    }
    ))
  }

  def cancelPatientTransfer(e: Exchange) = {
    logger.info("Calling cancelPatientTransfer")
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val wi = getWardIdentifier.toSuccess("Could not locate ward identifier").toValidationNel
    val bn = getMessageValue("bed").successNel[String]
    waitAndErr((hn |@| wi |@| bn)((i, w, b) => {
      val args: List[OEType] = List(i, w)
      val bed: Option[List[OEDictionary]] = b.map(z => List(OEDictionary("bed" -> OEString(z))))
      connector.callMethod("t4clinical.patient", "cancelTransfer", (args ++ ~bed): _*)
    }))
  }

  def visitNew(e:Exchange) : Unit = {
    logger.info("Calling visitNew")
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val wi = getWardIdentifier.toSuccess("Could not locate ward identifier.").toValidationNel
    val vi = getVisitName.toSuccess("Could not locate visit identifier.").toValidationNel
    val sdt = (getMessageValue("visit_start_date_time") | new DateTime().toString(toDateTimeFormat)).successNel
    val om = getMapFromFields(ConfigHelper.optionalVisitFields).successNel

    waitAndErr((hn |@| wi |@| vi |@| sdt |@| om)((i, w, v, vs, o) => {
      connector.callMethod("t4clinical.patient.visit", "visitNew", i, w, v, vs, OEDictionary(o.mapValues(OEString)))
    }))
  }

  def patientUpdate(e:Exchange) = {
    logger.info("calling patientUpdate")
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val om = getMapFromFields(ConfigHelper.optionalPatientFields).successNel
    waitAndErr((hn |@| om)( (h,o) => {
      connector.callMethod("t4clinical.patient", "patientUpdate", h, OEDictionary(o.mapValues(OEString)))
    }))
  }

  def patientDischarge(e: Exchange) = {
    logger.info("calling patientDischarge")
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val dd = (getMessageValue("discharge_date") | new DateTime().toString(toDateTimeFormat)).successNel
    waitAndErr((hn |@| dd)( connector.callMethod("t4clinical.patient", "patientDischarge", _,_)))
  }

  def cancelPatientDischarge(e: Exchange) = {
    logger.info("calling cancelPatientDischarge")
    implicit val t = getTerser(e)
    val vi = getVisitName.toSuccess("Could not locate visit identifier.").toValidationNel
    waitAndErr(vi.map(connector.callMethod("t4clinical.patient.visit","cancelDischarge", _)))
  }
  def patientNew(e: Exchange) = {
    logger.info("calling patientNew")
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val om = getMapFromFields(ConfigHelper.optionalPatientFields).successNel
    waitAndErr((hn |@| om)((h,o) => {
      connector.callMethod("t4clinical.patient", "patientNew", h, OEDictionary(o.mapValues(OEString)))
    }))
  }


  def cancelVisitNew(e: Exchange) = {
    logger.info("calling cancelVisitNew")
    implicit val t = getTerser(e)
    val vi = getVisitName.toSuccess("Could not locate visit identifier.").toValidationNel
    waitAndErr(vi.map(connector.callMethod("t4clinical.patient.visit", "cancelVisit", _)))
  }

  def visitUpdate(e: Exchange) = {
    logger.info("calling visitUpdate")
    implicit val t = getTerser(e)
    val hn = getHospitalNumber.toSuccess("Could not locate hospital number").toValidationNel
    val wi = getWardIdentifier.toSuccess("Could not locate ward identifier.").toValidationNel
    val vi = getVisitName.toSuccess("Could not locate visit identifier.").toValidationNel
    val sdt = (getMessageValue("visit_start_date_time") | new DateTime().toString(toDateTimeFormat)).successNel
    val om = getMapFromFields(ConfigHelper.optionalVisitFields).successNel

    waitAndErr((hn |@| wi |@| vi |@| sdt |@| om)((h,w,v,vs,o) =>
     connector.callMethod("t4clinical.patient.visit", "visitUpdate", h, w, v,vs, OEDictionary(o.mapValues(OEString)))))
  }
}
