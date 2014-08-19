package com.tactix4.t4ADT

import com.tactix4.t4skr.T4skrSession


import scala.concurrent.Await
import scala.concurrent.duration._


import com.tactix4.t4skr.core._
import com.tactix4.t4openerp.connector.domain.Domain._
import com.tactix4.t4openerp.connector._

/**
 * Created by max on 02/06/14.
 */
trait T4skrQueries {

  val connector:T4skrSession
  val timeOutMillis: Long

  def getPatientByHospitalNumber(hospitalNumber: HospitalNo): Option[T4skrId] =
    Await.result(connector.oeSession.search("t4clinical.patient", "other_identifier" === hospitalNumber).value, timeOutMillis millis).fold(
      _ => None,
      ids =>  ids.headOption
    )

  def getPatientByNHSNumber(nhsNumber: String): Option[T4skrId] =
    Await.result(connector.oeSession.search("t4clinical.patient", "patient_identifier" === nhsNumber).value, timeOutMillis millis).fold(
      _ => None,
      ids => ids.headOption
    )

  def getPatientByVisitId(vid: Int): Option[T4skrId] = {
    Await.result(connector.oeSession.read("t4clinical.patient.visit", List(vid), List("patient_id")).value, timeOutMillis millis).fold(
      _ => None,
      ids => for {
        h <- ids.headOption
        oe <- h.get("patient_id")
        a <- oe.array
        h <- a.headOption
        id <- h.int
      } yield id
    )
  }

  def getVisit(visitId: VisitId): Option[Int] =
    Await.result(connector.oeSession.search("t4clinical.patient.visit", "name" === visitId).value, timeOutMillis millis).fold(
      _ => None,
      ids => ids.headOption
    )
}
