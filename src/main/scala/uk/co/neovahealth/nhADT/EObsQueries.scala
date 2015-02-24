package uk.co.neovahealth.nhADT

import uk.co.neovahealth.nhADT.utils.ConfigHelper

import scala.language.postfixOps
import scala.language.implicitConversions
import ConfigHelper
import com.tactix4.t4openerp.connector._
import com.tactix4.t4openerp.connector.transport.OEType
import com.tactix4.t4openerp.connector.domain.Domain._
import com.typesafe.scalalogging.slf4j.{StrictLogging, LazyLogging}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Await}
import scalaz.std.option.optionSyntax._
import scalaz.std.option._
import scalaz.syntax.all._
/**
 * Created by max on 02/06/14.
 */
trait EObsQueries extends StrictLogging{
  implicit val ec: ExecutionContext

  val session:OESession
  val timeOutMillis: Long = ConfigHelper.timeOutMillis

  def getPatientByHospitalNumber(hospitalNumber: String): Option[Int] =
    Await.result(session.search("nh.clinical.patient", "other_identifier" === hospitalNumber).run, timeOutMillis millis).fold(
      message => {logger.error("Error getting patientByHospitalNumber: " + message);None},
      ids =>  ids.headOption
    )

  def getPatientByNHSNumber(nhsNumber: String): Option[Int] =
    Await.result(session.search("nh.clinical.patient", "patient_identifier" === nhsNumber).run, timeOutMillis millis).fold(
      message => {logger.error("Error getting patientByNHSNumber: " + message);None},
      ids => ids.headOption
    )

  def getPatientByVisitId(vid: Int): Option[Int] = {
    val r = for {
      r1  <- session.read("nh.activity", List(vid), List("patient_id"))
      pid <- ((r1.headOption >>= (_.get("patient_id")) >>= (_.int)) \/> "error getting patient_id").asOER
    } yield pid
    Await.result(r.run, timeOutMillis millis).toOption
  }

  def getVisit(visitCode: String): Option[Int] =
    Await.result(session.searchAndRead("nh.clinical.spell", "code" === visitCode, List("activity_id")).run, timeOutMillis millis).fold(
      message => {logger.info("Could not find visit: " + message); None},
      result => for {
        h   <- result.headOption
        aid <- h.get("activity_id")
        i   <- aid.array >>= (_.headOption) >>= (_.int)
      } yield i
    )
}
