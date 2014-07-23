package com.tactix4.t4ADT

import com.tactix4.t4skr.T4skrSession


import scala.concurrent.Await
import scala.concurrent.duration._


import com.tactix4.t4skr.core._
import com.tactix4.t4openerp.connector.domain.Domain._
import com.tactix4.t4openerp.connector._

import scalaz.Success

/**
 * Created by max on 02/06/14.
 */
trait T4skrQueries {

  val connector:T4skrSession

  def getPatientByHospitalNumber(hospitalNumber: HospitalNo): Option[T4skrId] =
    Await.result(connector.oeSession.search("t4clinical.patient", "other_identifier" === hospitalNumber).value, 2000 millis).fold(
      _ => None,
      ids =>  ids.headOption
    )

  def getPatientByNHSNumber(nhsNumber: String): Option[T4skrId] =
    Await.result(connector.oeSession.search("t4clinical.patient", "patient_identifier" === nhsNumber).value, 2000 millis).fold(
      _ => None,
      ids => ids.headOption
    )

  def getPatientByVisitId(vid: Int): Option[T4skrId] = {
    Await.result(connector.oeSession.read("t4clinical.patient.visit", List(vid), List("patient_id")).value, 2000 millis) match {
      case Success(x) =>
      case  => for {
        h <- ids.headOption
        oe <- h.get("patient_id")
        a <- oe.array
        h <- a.headOption
        id <- h.int
      } yield id
    }
  }

  def getVisit(visitId: VisitId): Option[Int] =
    Await.result(connector.oeSession.search("t4clinical.patient.visit", "name" === visitId).value, 2000 millis).fold(
      _ => None,
      ids => ids.headOption
    )
}
