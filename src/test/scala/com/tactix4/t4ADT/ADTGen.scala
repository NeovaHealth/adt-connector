package com.tactix4.t4ADT

import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import scala.io.Source
import scala.util.Random
import scalaz._
import Scalaz._
import org.scalacheck.Gen
import com.tactix4.t4skr.core.VisitId

/**
 * Created by max on 03/06/14.
 */
trait ADTGen extends VisitGen{

  case class Patient(hospitalNo:String,nhsNumber:Option[String],givenName: Option[String], middleNames: Option[String], familyName: Option[String], title: Option[String], mothersMaidenName: Option[String], dob: Option[DateTime], sex: Option[String], streetAddress: Option[String], city: Option[String], state: Option[String], zip: Option[String], country: Option[String], phone: Option[String]){
  }
  case class Doctor(givenName: String, middleNames: String, familyName: String, title: String, id: String) {
    override val toString = s"$title $givenName $familyName".trim.replaceAll("  "," ")
  }

  case class MSHSegment(msgType:String,date:String, id:String){
    override val toString =  s"MSH|^~\\&|Sender|Sender|Receiver|Receiver|$date||ADT^$msgType|$id||2.4"
  }
  //http://www.mexi.be/documents/hl7/ch300055.htm
  case class EVNSegment(msgType:String,date:String, dtPlannedEvn: Option[String], evnReasonCode: Option[String], opID: Option[String], evnOccured:Option[String]){
    override val toString = s"EVN|$msgType|$date|${~dtPlannedEvn}|${~evnReasonCode}||${~evnOccured}"
  }
  case class PIDSegment(p:Patient){
    override val toString:String = s"PID|1|^^^^PAS||${p.hospitalNo}^${~p.nhsNumber}|${~p.familyName}^${~p.givenName}^${~p.middleNames}^^${~p.title}|${~p.mothersMaidenName}|${~p.dob.map(_.toString(toDateTimeFormat))}|${~p.sex}|||${~p.streetAddress}^^${~p.city}^${~p.state}^${~p.zip}^${~p.country}^^^^^^||${~p.phone}|||||||||||N"
  }

  case class PV1Segment(wardCode:String,bed:Option[Int],wardName:Option[String],consultingDoctor:Doctor,referringDoctor:Doctor,admittingDoctor:Doctor,hospitalService:Option[String],patientType:Option[String],visitID:VisitId,admitDate:Option[DateTime],dischargeDate:Option[DateTime]){
    override def toString:String =
    s"PV1|1|I|$wardCode^^${bed.map(_.toString) | ""}^^^^^^${~wardName}|11||||${referringDoctor.id}^${referringDoctor.familyName}^${referringDoctor.givenName}^${referringDoctor.middleNames}^^${referringDoctor.title}|" +
      s"${consultingDoctor.id}^${consultingDoctor.familyName}^${consultingDoctor.givenName}^${consultingDoctor.middleNames}^^${consultingDoctor.title}|${~hospitalService}|||||||" +
      s"${admittingDoctor.id}^${admittingDoctor.familyName}^${admittingDoctor.givenName}^${admittingDoctor.middleNames}^^${admittingDoctor.title}|${~patientType}|$visitID|||||||" +
      s"||||||||||||||||||${~admitDate.map(_.toString(toDateTimeFormat))}|${~dischargeDate.map(_.toString(toDateTimeFormat))}"
  }

  case class ADTMsg(msh:MSHSegment,evn:EVNSegment,pid:PIDSegment,pv1:PV1Segment){
    override def toString:String =
    s"$msh\r$evn\r$pid\r$pv1"
  }


  case class VisitState(event:Event,pv:PV1Segment, pid:PIDSegment)

  val fromDateTimeFormat = DateTimeFormat.forPattern("MM/dd/yyyy")
  val toDateTimeFormat = DateTimeFormat.forPattern("yyyyMMddHHmmss")
  val oerpDateTimeFormat = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")

  val patients = Source.fromFile("src/test/resources/fake_patients.csv").getLines()
  val doctors = Source.fromFile("src/test/resources/fake_doctors.csv").getLines()

  val hospitalServices = List("110", "160")
  val patientTypes = List("01")

//  val wards = Map("WARD E8" -> "E8", "WARD E9" -> "E9")
    val wards = Map("ELIZABETH FRY" -> "EFMB", "ELIZABETH FRY VIRTUAL WARD" -> "EFVW")

  def modifyPid(pid:PIDSegment):Gen[PIDSegment] = {
    pid.copy(p = pid.p.copy(givenName = pid.p.givenName.reverse))
  }

  def modifyPv1(pv1:PV1Segment):Gen[PV1Segment] = {
    for {
      np <- PV1(pv1.admitDate,pv1.dischargeDate)
      cd <- Gen.frequency((5,pv1.consultingDoctor), (1, np.consultingDoctor))
      wc <- Gen.frequency((5, pv1.wardCode),(1, np.wardCode))
      a = wardCodePV1Lens.set(pv1,wc)
      b = consultingDoctorLens.set(a,cd)
    } yield b
  }

  

  val consultingDoctorLens: Lens[PV1Segment, Doctor] = Lens.lensu(
    get = (_:PV1Segment).consultingDoctor,
    set = (pv1:PV1Segment, d: Doctor) => pv1.copy(consultingDoctor = d)
  )

  val admitDatePV1Lens: Lens[PV1Segment, Option[DateTime]] = Lens.lensu(
    get = (_: PV1Segment).admitDate,
    set = (pv1: PV1Segment, a: Option[DateTime]) => pv1.copy(admitDate = a)
  )
  val dischargeDatePV1Lens: Lens[PV1Segment, Option[DateTime]] = Lens.lensu(
    get = (_: PV1Segment).dischargeDate,
    set = (pv1: PV1Segment, dd: Option[DateTime]) => pv1.copy(dischargeDate = dd)
  )
  val wardCodePV1Lens: Lens[PV1Segment, String] = Lens.lensu(
    get = (_: PV1Segment).wardCode,
    set = (pv1: PV1Segment, wc: String) => pv1.copy(wardCode = wc)
  )
  val wardNamePV1Lens: Lens[PV1Segment, Option[String]] = Lens.lensu(
    get = (_: PV1Segment).wardName,
    set = (pv1: PV1Segment, wn: Option[String]) => pv1.copy(wardName = wn)
  )

  val pidVisitStateLens: Lens[VisitState, PIDSegment] = Lens.lensu(
    get = (_:VisitState).pid,
    set = (s:VisitState,p:PIDSegment) => s.copy(pid = p)
  )
  
  val pv1VisitStateLens: Lens[VisitState, PV1Segment] = Lens.lensu(
    get = (_:VisitState).pv,
    set = (msg:VisitState, pv1:PV1Segment) => msg.copy(pv = pv1))

  val eventOccuredLens: Lens[EVNSegment, Option[String]] = Lens.lensu(
    get = (_:EVNSegment).evnOccured,
    set = (evn:EVNSegment, s:Option[String]) => evn.copy(evnOccured = s)
  )
  val evnMsgLens: Lens[ADTMsg, EVNSegment] = Lens.lensu(
    get = (_:ADTMsg).evn,
    set = (msg:ADTMsg, e:EVNSegment) => msg.copy(evn = e)
  )

  val eventOccured = evnMsgLens >=> eventOccuredLens

  val admitDate = pv1VisitStateLens >=> admitDatePV1Lens

  val wardCode = pv1VisitStateLens >=> wardCodePV1Lens

  val wardName = pv1VisitStateLens >=> wardNamePV1Lens

  val dischargeDate = pv1VisitStateLens >=> dischargeDatePV1Lens


  val vs = StateT[Gen,VisitState, ADTMsg]{ s =>

    val result = s.event match {

      case NothingEvent => for {
        msg <- genMsg("XX",s)
      } yield s -> msg

      case AdmitEvent => for {
        ns <- Gen.const(admitDate.set(s, DateTime.now().some))
        e <- visitGenerator.eval(Admitted)
        msg <- genMsg("A01",ns)
      } yield ns.copy(event = e) -> msg

      case CancelAdmitEvent => for {
        ns <- Gen.const(admitDate.set(s,None))
        e <- visitGenerator.eval(VisitEnd)
        msg <- genMsg("A11",ns)
      } yield ns.copy(event = e) -> msg

      case TransferEvent => for {
        (wn,wc) <- Gen.oneOf(wards.toSeq)
        ns <- Gen.const(wardCode.set(s,wc))
        ns2 <- Gen.const(wardName.set(ns,wn.some))
        e <- visitGenerator.eval(Admitted)
        npv <- modifyPv1(ns2.pv)
        ns3 <- Gen.const(pv1VisitStateLens.set(ns2,npv))
        msg <- genMsg("A02", ns3)
      } yield  ns3.copy(event = e) -> msg

      case CancelTransferEvent => for {
        (wn,wc) <- Gen.oneOf(wards.toSeq)
        ns <- Gen.const(wardCode.set(s,wc))
        ns2 <- Gen.const(wardName.set(ns,wn.some))
        e <- visitGenerator.eval(Admitted)
        npv <- modifyPv1(ns2.pv)
        ns3 <- Gen.const(pv1VisitStateLens.set(ns2,npv))
        msg <- genMsg("A12", ns3)
      } yield  ns3.copy(event = e) -> msg

      case DischargeEvent => for {
        ns <- Gen.const(dischargeDate.set(s, DateTime.now().some))
        msg <- genMsg("A03",ns)
        e <- visitGenerator.eval(Discharged)
      } yield ns.copy(event = e) -> msg

      case CancelDischargeEvent => for{
        ns <- Gen.const(dischargeDate.set(s, None))
        npv <- modifyPv1(ns.pv)
        ns2 <- Gen.const(pv1VisitStateLens.set(ns,npv))
        e <- visitGenerator.eval(Admitted)
        msg <- genMsg("A13",ns2)
      } yield ns2.copy(event = e) -> msg


      case UpdatePersonEvent => for {
        np  <- modifyPid(s.pid)
        ns  <- Gen.const(pidVisitStateLens.set(s,np))
        msg <- genMsg("A31", ns)
        e   <- visitGenerator.eval(Admitted)
      } yield ns.copy(event = e) -> msg

      case UpdatePatientEvent => for {
        np  <- modifyPid(s.pid)
        npv <- modifyPv1(s.pv)
        ns  <- Gen.const(pidVisitStateLens.set(s,np))
        ns2 <- Gen.const(pv1VisitStateLens.set(s,npv))
        msg <- genMsg("A08", ns2)
        e   <- visitGenerator.eval(Admitted)
      } yield ns2.copy(event = e) -> msg

      case CreatePatientEvent => for {
        np <- PID
        ns <- Gen.const(pidVisitStateLens.set(s,np))
        msg <- Gen.oneOf(genMsg("A05",ns),genMsg("A28",ns))
        e <- visitGenerator.eval(PatientCreated)
      } yield ns.copy(event = e) -> msg
      //TODO this won't work

      case MergePatientsEvent => for {
        ns <- Gen.const(s)
        msg <- genMsg("A40",ns)
        e <- visitGenerator.eval(Admitted)
      } yield ns.copy(event = e) -> msg
    }
    result
  }

  val createVisit: Gen[List[ADTMsg]] = {
    val list:Gen[List[ADTMsg]] = for {
       pv1 <- PV1(None,None)
       pid <- PID
       state <- Gen.oneOf(AdmitEvent,CancelAdmitEvent,DischargeEvent,CancelDischargeEvent,MergePatientsEvent,UpdatePatientEvent,UpdatePersonEvent,TransferEvent,CancelTransferEvent)
       ss <- Gen.const(VisitState(state, pv1, pid))
       x <- vs.replicateM(10).eval(ss)
     } yield x.filter(_.msh.msgType != "XX")

    list.map(l => {
      l.map(m => {
        if(m.msh.msgType == "A08") {
          //grab all msg previous to the a08 and shuffle them
          val r = Random.shuffle(l.takeWhile(_ != m))
            //find the first A01/02/03
            .find(x => x.msh.msgType == "A01" || x.msh.msgType == "A02" || x.msh.msgType == "A03")
             .map(s =>{
              //set this msgs evnOccured to the found one
              eventOccured.set(m,s.evn.evnOccured)
          })
          r.getOrElse(m)
        }
        else{
          m
        }
      }).filter(_.msh.msgType != "A40")
    })
//    list.map(l => {
//      //get all a08s
//      val a08s = l.filter(_.msh.msgType == "A08")
//      //get all A01/A02/A03
//      val others = Random.shuffle(l.filter(m => m.msh.msgType == "A01" || m.msh.msgType == "A02" || m.msh.msgType == "A03"))
//      //for each A08
//      a08s.flatMap(aoe =>{
//        val i = l.indexOf(aoe)
//        //find an A01/A02/A03 that came before it
//        val j = others.find(o => l.indexOf(o) < i)
//        //and set the A08's eventOccured date to it
//        j.map(o => eventOccured.set(aoe,o.evn.evnOccured))
//      })
//    })
  }


//  val VisitGen:Gen[List[ADTMsg]] ={
//    for {
//      msh <- MSH("","")
//      evn <- EVN("","",None,None,None,Some(DateTime.now.toString(toDateTimeFormat)))
//      pv1 <- PV1(None,None)
//      pid <- PID
//      msgL <- Gen.listOfN(10,Gen.const(ADTMsg(msh,evn,pid,pv1)))
//    } yield msgL
//  }


  def genMsg(msgType:String, s:VisitState):Gen[ADTMsg] = {
    for {
      dt <- Gen.choose(-60,60).map(v => DateTime.now.minusMinutes(v).toString(toDateTimeFormat))
      msh <- MSH(msgType, dt)
      evn <- EVN(msgType, dt,None,None,None,dt.some)
    } yield ADTMsg(msh,evn,s.pid,s.pv)
  }

  def MSH(msgType: String, date: String): Gen[MSHSegment] = {
    for {
      id <- Gen.listOfN(25,Gen.alphaNumChar).map(_.mkString)
    } yield MSHSegment(msgType,date,id)
  }

  def EVN(msgType:String,date:String, dtPlannedEvn: Option[String], evnReasonCode: Option[String], opID: Option[String], evnOccured:Option[String]): Gen[EVNSegment] = {
    EVNSegment(msgType,date,dtPlannedEvn,evnReasonCode,opID,evnOccured)
  }

  val genPatient: Gen[Patient] =
    for {
      lineNo <- Gen.choose(1, patients.size - 1)
      p = Source.fromFile("src/test/resources/fake_patients.csv").getLines() drop lineNo next() split ","
      ps = p.map(x => Gen.option(Gen.const(x)))
      gn <- ps(2)
      mn <- ps(3)
      fn = Some(p(4)) // put family name back as optional once bug fixed in OERP
      t <- ps(1)
      mm <- ps(13)
      dob <- ps(14)
      s <- ps(0)
      street <- ps(5)
      city <- ps(6)
      state <- ps(7)
      zip <- ps(8)
      country <- ps(9)
      phone <- ps(11)
      hospitalNum <- Gen.listOfN(10, Gen.alphaNumChar).map(_.mkString)
      nhsNum <- Gen.option(Gen.listOfN(10, Gen.numChar).map(_.mkString))
      //TODO: put title back
    } yield Patient(hospitalNum, nhsNum, gn,mn,fn,None,mm,dob.map(d => DateTime.parse(d,fromDateTimeFormat)),s.flatMap(_.headOption.map(_.toString)),street,city,state,zip,country,phone)


  val genDoctor = for {
    l <- Gen.choose(0, doctors.size - 1)
    d = Source.fromFile("src/test/resources/fake_doctors.csv").getLines().drop(l).next.split(",")
    ds = d.map(x => Gen.const(x))
    gn <- ds(2)
    mn <- ds(3)
    fn <- ds(4)
    t <- ds(1)
    id = (d(8) + d(12)).replace(" ", "")
    //TODO: put title back
  } yield Doctor(gn,mn,fn,"",id)

  def PID: Gen[PIDSegment] = {
    for {
      p <- genPatient
    } yield PIDSegment(p)
  }

  def PV1(admitDate: Option[DateTime], dischargeDate: Option[DateTime]): Gen[PV1Segment] = {
    for {
      wardName <- Gen.oneOf(wards.keys.toList)
      bed <- Gen.option(Gen.choose(1, 24))
      wardCode = wards(wardName)
      consultingDoctor <- genDoctor
      referringDoctor <- genDoctor
      admittingDoctor <- genDoctor
      hospitalService <- Gen.option(Gen.oneOf(hospitalServices))
      patientType <- Gen.option(Gen.oneOf(patientTypes))
      visitID <- Gen.listOfN(10, Gen.alphaNumChar).map(_.mkString)
    } yield PV1Segment(wardCode,None,Some(wardName),consultingDoctor,referringDoctor,admittingDoctor,hospitalService,patientType,visitID,admitDate,dischargeDate)
  }

}