package com.neovahealth.nhADT

import com.neovahealth.nhADT.utils.ConfigHelper
import com.tactix4.t4openerp.connector._
import com.tactix4.t4openerp.connector.transport.OEType
import com.tactix4.t4openerp.connector.domain.Domain._
import com.typesafe.scalalogging.slf4j.LazyLogging
import scala.concurrent.duration._
import scala.concurrent.Await

/**
 * Created by max on 02/06/14.
 */
trait EObsQueries extends LazyLogging {

  val connector:OESession
  val timeOutMillis: Long = ConfigHelper.timeOutMillis

  def getPatientByHospitalNumber(hospitalNumber: String): Option[Int] =
    Await.result(connector.search("t4clinical.patient", "other_identifier" === hospitalNumber).run, timeOutMillis millis).fold(
      message => {logger.error(message);None},
      ids =>  ids.headOption
    )

  def getPatientByNHSNumber(nhsNumber: String): Option[Int] =
    Await.result(connector.search("t4clinical.patient", "patient_identifier" === nhsNumber).run, timeOutMillis millis).fold(
      message => {logger.error(message);None},
      ids => ids.headOption
    )

  def getPatientByVisitId(vid: Int): Option[Int] = {
    Await.result(connector.read("t4clinical.patient.visit", List(vid), List("patient_id")).run, timeOutMillis millis).fold(
      message => {logger.error(message); None},
      (ids: List[Map[String, OEType]]) => for {
        h  <- ids.headOption
        oe <- h.get("patient_id")
        a  <- oe.array
        h  <- a.headOption
        id <- h.int
      } yield id
    )
  }

  def getVisit(visitId: String): Option[Int] =
    Await.result(connector.search("t4clinical.patient.visit", "name" === visitId).run, timeOutMillis millis).fold(
      message => {logger.error(message); None},
      ids => ids.headOption
    )
}
