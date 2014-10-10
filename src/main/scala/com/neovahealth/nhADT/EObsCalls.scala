package com.neovahealth.nhADT

import ca.uhn.hl7v2.util.Terser
import com.neovahealth.nhADT.exceptions.ADTExceptions
import com.neovahealth.nhADT.utils.ConfigHelper
import com.tactix4.t4openerp.connector._
import com.tactix4.t4openerp.connector.transport.{OEDictionary, OEString, OEType}
import com.typesafe.scalalogging.slf4j.LazyLogging
import org.apache.camel.Exchange
import org.joda.time.DateTime

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scalaz._
import Scalaz._

/**
 * Created by max on 02/06/14.
 */
trait EObsCalls extends ADTProcessing with ADTExceptions with EObsQueries with LazyLogging {

  def getHeader[T:ClassTag](name:String,e:Exchange):Option[T] = e.getIn.getHeader(name,None,classOf[Option[T]])
  def hasHeader(name:String)(implicit e:Exchange) : Boolean = getHeader[Any](name,e).isDefined
  def setHeaderValue(name:String, o:Any)(implicit e:Exchange) = e.getIn.setHeader(name,o)

  val triggerEventHeader = "CamelHL7TriggerEvent"

  def getMapFromFields(m:List[String])(implicit t:Terser): Map[String, String] =
    m.map(f => getMessageValue(f).map(v => f -> v)).flatten.toMap


  def visitExists(e: Exchange): Boolean =  hasHeader("visitId")(e)

  def patientExists(e: Exchange): Boolean =  hasHeader("patientLinkedToHospitalNo")(e)


  def mergeTargetExists(e: Exchange): Boolean = {
    getOldHospitalNumber(getTerser(e)).flatMap(getPatientByHospitalNumber).isDefined
  }

  def isSupportedWard(e:Exchange): Boolean = getWardIdentifier(getTerser(e)).fold(true)(w =>ConfigHelper.wards.exists(_.findFirstIn(w).isDefined))

  def msgType(e:Exchange) = {
    e.getIn.getHeader(triggerEventHeader, "", classOf[String])
  }

  def getTerser(e:Exchange):Terser = e.getIn.getHeader("terser", classOf[Terser])

  def getPatientLinkedToHospitalNo(implicit e:Exchange) : Option[Int] = getHeader[Int]("patientLinkedToHospitalNo",e)

  def getPatientLinkedToVisit(implicit e:Exchange) : Option[Int] = getHeader[Int]("patientLinkedToVisit",e)

  def getPatientLinkedToNHSNo(implicit e:Exchange) : Option[Int] = getHeader[Int]("patientLinkedToNHSNo",e)

  def waitAndErr(x:ValidationNel[String,OEResult[_]]): Unit = x.fold(
      errors => throw new ADTFieldException(errors.list.mkString(", ")),
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
