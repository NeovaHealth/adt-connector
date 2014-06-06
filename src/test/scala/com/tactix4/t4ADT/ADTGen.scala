package com.tactix4.t4ADT

import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import scala.io.Source
import scalaz.syntax.applicative._
import scalaz._
import Scalaz._
import org.scalacheck.Gen
import com.tactix4.t4skr.core.VisitId
import scala.util.Random

/**
 * Created by max on 03/06/14.
 */
class ADTGen extends VisitGen{

  case class Patient(hospitalNo:String,nhsNumber:String,givenName: String, middleNames: String, familyName: String, title: String, mothersMaidenName: String, dob: String, sex: String, streetAddress: String, city: String, state: String, zip: String, country: String, phone: String){
    override def toString:String = s"PID|1|^^^^PAS||$hospitalNo^|$familyName^$givenName^^^$title|$mothersMaidenName|$dob|$sex|||$streetAddress^^$city^$state^$zip^$country^^^^^^||$phone|||||||||||N"
  }
  case class Doctor(givenName: String, middleNames: String, familyName: String, title: String, id: String)

  case class PIDSegment(p:Patient){
    override def toString = s"PID|1|^^^^PAS||${p.hospitalNo}^${p.nhsNumber}|${p.familyName}^${p.givenName}^^^${p.title}|${p.mothersMaidenName}|${p.dob}|${p.sex}|||${p.streetAddress}^^${p.city}^${p.state}^${p.zip}^${p.country}^^^^^^||${p.phone}|||||||||||N"
  }

  case class PV1Segment(wardCode:String,bed:Int,wardName:String,consultingDoctor:Doctor,referringDoctor:Doctor,admittingDoctor:Doctor,hospitalService:String,patientType:String,visitID:VisitId,admitDate:String,dischargeDate:String){
    override def toString:String =
    s"PV1|1|I|$wardCode^^$bed^^^^^^$wardName|11||||${referringDoctor.id}^${referringDoctor.familyName}^${referringDoctor.givenName}^${referringDoctor.middleNames}^^${referringDoctor.title}|" +
      s"${consultingDoctor.id}^${consultingDoctor.familyName}^${consultingDoctor.givenName}^${consultingDoctor.middleNames}^^${consultingDoctor.title}|$hospitalService|||||||" +
      s"${admittingDoctor.id}^${admittingDoctor.familyName}^${admittingDoctor.givenName}^${admittingDoctor.middleNames}^^${admittingDoctor.title}|$patientType|$visitID|||||||" +
      s"||||||||||||||||||$admitDate|$dischargeDate"
  }
  case class ADTMsg(msh:String,evn:String,pid:PIDSegment,pv1:PV1Segment)
  case class VisitState(event:Event,pv:PV1Segment, pid:PIDSegment)

  val fromDateTimeFormat = DateTimeFormat.forPattern("MM/dd/yyyy")
  val toDateTimeFormat = DateTimeFormat.forPattern("yyyyMMddHHmmss.SSSZ")

  val messageTypes = List("A01", "A11", "A02", "A12", "A03", "A13", "A05", "A08", "A31", "A28")
  val patients = Source.fromFile("src/test/resources/fake_patients.csv").getLines()
  val doctors = Source.fromFile("src/test/resources/fake_doctors.csv").getLines()

  val hospitalServices = List("110", "160")
  val patientTypes = List("01")

  val wards = Map("E8" -> "Test Ward E8", "E9" -> "Test Ward E9")

  def modifyPid(pid:PIDSegment):PIDSegment = {
    pid.copy(p = pid.p.copy(givenName = pid.p.givenName.reverse))
  }

  val vs = State[VisitState, ()]{ s => s.event match {
    case AdmitEvent => (s.copy(pv = s.pv.copy(admitDate = DateTime.now().toString(toDateTimeFormat))),())
    case CancelAdmitEvent => (s.copy(pv = s.pv.copy(admitDate = "")),())
    case TransferEvent => (s.copy(pv = s.pv.copy(wardCode = Random.shuffle(wards.keys).head)),())
    case CancelTransferEvent => (s.copy(pv = s.pv.copy(wardCode = Random.shuffle(wards.keys).head)),())
    case DischargeEvent => (s.copy(pv = s.pv.copy(dischargeDate = DateTime.now().toString(toDateTimeFormat))),())
    case CancelDischargeEvent => (s.copy(pv = s.pv.copy(dischargeDate = "")),())
    case UpdatePatientEvent => (s.copy(pid = modifyPid(s.pid)),())
    case CreatePatientEvent => (s,())
    case MergePatientsEvent => (s,())
  }
  }

  def createVisit = {
    val visitEvents: List[Event] = visitGenerator.replicateM(10).eval(Start) takeWhile (_ != NothingEvent)
    val result: Gen[List[VisitState]] = for {
      pv1 <- PV1("", "")
      pid <- PID
      startingState = VisitState(NothingEvent, pv1, pid)
      x: List[Id.Id[VisitState]] = visitEvents.map(e => vs.exec(startingState.copy(event = e)))
    } yield x

    result
  }

//    val messages: List[State[VisitState,()] => Gen[String]] = visitEvents.map {
//      case AdmitEvent => A01(_)
//      case CancelAdmitEvent => A11(_)
//      case TransferEvent => A02(_)
//      case CancelTransferEvent => A12(_)
//      case DischargeEvent => A03(_)
//      case CancelDischargeEvent => A13(_)
//      case UpdatePatientEvent => (Random.nextDouble() > 0.5) ? A08(_) | A31(_)
//      case CreatePatientEvent => (Random.nextDouble() > 0.5) ? A01(_) | A05(_)
//      case MergePatientsEvent => A40(_)
//    }
//
//    for {
//      visit <- PV1(DateTime.now().toString(toDateTimeFormat),"")
//      msg = messages.map(_(visit))
//    }
//
//  }
  def A12(pv1: PV1Segment): Gen[String]
  def A13(pv1: PV1Segment): Gen[String]
  def A05(pv1: PV1Segment): Gen[String]
  def A08(pv1: PV1Segment): Gen[String]
  def A28(pv1: PV1Segment): Gen[String]
  def A31(pv1: PV1Segment): Gen[String]
  def A40(pv1: PV1Segment): Gen[String]
//
//  def A01(s:State[VisitState,()] :Gen[String] = {
//    val dt = DateTime.now.toString(toDateTimeFormat)
//    for {
//      msh <- MSH("A01", dt)
//      evn <- EVN("A01", dt)
//    } yield msh + "\\r" + evn + "\\r" + pv1.toString
//  }
//  def A11(pv1: PV1Segment): Gen[String] = {
//    val dt = DateTime.now.toString(toDateTimeFormat)
//    for {
//      msh <- MSH("A11", dt)
//      evn <- EVN("A11", dt)
//    } yield msh + "\\r" + evn + "\\r" + pv1.toString
//}
//
//  def A02(pv1: PV1Segment): Gen[String] = {
//    val dt = DateTime.now.toString(toDateTimeFormat)
//    for {
//      msh <- MSH("A02", dt)
//      evn <- EVN("A02", dt)
//      npv1 = pv1.copy(wardCode = Random.shuffle(wards.keys).head)
//    } yield msh + "\\r" + evn + "\\r" + npv1.toString
//  }
//
//  def A03(pv1: PV1Segment): Gen[String] = {
//    val dt = DateTime.now.toString(toDateTimeFormat)
//    for {
//      msh <- MSH("A03", dt)
//      evn <- EVN("A03", dt)
//      npv1 = pv1.copy(dischargeDate = dt)
//    } yield msh + "\\r" + evn + "\\r" + npv1.toString
//  }
//
//

  def MSH(msgType: String, date: String): Gen[String] = {
    for {
      id <- Gen.choose(0, Int.MaxValue)
    } yield "MSH|^~\\&|Sender|Sender|Receiver|Receiver|" +
      s"$date||$msgType|$id||2.4"
  }

  def EVN(msgType: String, date: String): Gen[String] = {
    s"EVN|$msgType|$date"
  }

  val genPatient: Gen[Patient] =
    for {
      lineNo <- Gen.choose(1, patients.size - 1)
      p = Source.fromFile("src/test/resources/fake_patients.csv").getLines() drop lineNo next() split ","
      hospitalNum <- Gen.listOfN(10, Gen.alphaNumChar).map(_.mkString)
      nhsNum <- Gen.listOfN(10, Gen.numChar).map(_.mkString)
    } yield Patient(hospitalNum, nhsNum,p(2), p(3), p(4), p(1), p(13), DateTime.parse(p(14), fromDateTimeFormat).toString(toDateTimeFormat), p(0).charAt(0).toString, p(5), p(6), p(7), p(8), p(9), p(11))


  val genDoctor = for {
    l <- Gen.choose(0, doctors.size - 1)
    d = Source.fromFile("src/test/resources/fake_doctors.csv").getLines().drop(l).next.split(",")
  } yield Doctor(d(2), d(3), d(4), d(1), d(8).replace(" ", ""))

  def PID: Gen[PIDSegment] = {
    for {
      p <- genPatient
    } yield PIDSegment(p)
  }

  def PV1(admitDate: String, dischargeDate: String): Gen[PV1Segment] = {
    for {
      wardCode <- Gen.oneOf(wards.keys.toList)
      bed <- Gen.choose(1, 24)
      wardName = wards(wardCode)
      consultingDoctor <- genDoctor
      referringDoctor <- genDoctor
      admittingDoctor <- genDoctor
      hospitalService <- Gen.oneOf(hospitalServices)
      patientType <- Gen.oneOf(patientTypes)
      visitID <- Gen.listOfN(10, Gen.alphaNumChar).map(_.mkString)
    } yield PV1Segment(wardCode,bed,wardName,consultingDoctor,referringDoctor,admittingDoctor,hospitalService,patientType,visitID,admitDate,dischargeDate)
  }

}