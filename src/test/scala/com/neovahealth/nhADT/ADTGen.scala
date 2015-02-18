package com.neovahealth.nhADT

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.scalacheck.Gen

import scala.annotation.tailrec
import scala.io.Source
import scala.util.Random
import scalaz.Scalaz._
import scalaz._


/**
 * Created by max on 03/06/14.
 */

object adtGen extends ADTGen

trait ADTGen extends VisitGen{

  object Status extends Enumeration{
    type Status = Value
    val Unknown,Known,Admitted,Transferred,Discharged,Finished = Value
  }

  case class VisitState(event:Event,pv:Option[PV1Segment], pid:PIDSegment, status:Status.Value)

  val eventLens: Lens[VisitState,Event] = Lens.lensu(
    get = (_:VisitState).event,
    set = (vs:VisitState, e:Event) => vs.copy(event = e)

  )
  val stateLens: Lens[VisitState,Status.Value] = Lens.lensu(
    get =  (_:VisitState).status,
    set =  (vs:VisitState,s:Status.Value) => vs.copy(status = s)
  )

  val consultingDoctorLens: Lens[Option[PV1Segment], Option[Doctor]] = Lens.lensu(
    get = (_:Option[PV1Segment]).flatMap(_.consultingDoctor),
    set = (pv1:Option[PV1Segment], d: Option[Doctor]) => pv1.map(_.copy(consultingDoctor = d))
  )

  val admitDatePV1Lens: Lens[Option[PV1Segment], Option[DateTime]] = Lens.lensu(
    get = (_: Option[PV1Segment]).flatMap(_.admitDate),
    set = (pv1: Option[PV1Segment], a: Option[DateTime]) => pv1.map(_.copy(admitDate = a))
  )
  val dischargeDatePV1Lens: Lens[Option[PV1Segment], Option[DateTime]] = Lens.lensu(
    get = (_: Option[PV1Segment]).flatMap(_.dischargeDate),
    set = (pv1: Option[PV1Segment], dd: Option[DateTime]) => pv1.map(_.copy(dischargeDate = dd))
  )
  val wardCodePV1Lens:Lens[Option[PV1Segment], Option[String]] = Lens.lensu(
    get = (_: Option[PV1Segment]).flatMap(_.wardCode),
    set = (pv1: Option[PV1Segment], wc: Option[String]) => pv1.map(_.copy(wardCode = wc))
  )

  val wardNamePV1Lens: Lens[Option[PV1Segment], Option[String]] = Lens.lensu(
    get = (_: Option[PV1Segment]).flatMap(_.wardName),
    set = (pv1: Option[PV1Segment], wn: Option[String]) => pv1.map(_.copy(wardName = wn))
  )

  val pidVisitStateLens: Lens[VisitState, PIDSegment] = Lens.lensu(
    get = (_:VisitState).pid,
    set = (s:VisitState,p:PIDSegment) => s.copy(pid = p)
  )

  val pidPatientLens: Lens[PIDSegment, Patient] = Lens.lensu(
    get = (_:PIDSegment).p,
    set = (p:PIDSegment, pa:Patient) => p.copy(p = pa)
  )

  val patientFamilyNameLens: Lens[Patient, Option[String]] = Lens.lensu(
    get = (_:Patient).familyName,
    set = (p:Patient, n:Option[String]) => p.copy(familyName = n)
  )
  
  val pv1VisitStateLens: Lens[VisitState, Option[PV1Segment]] = Lens.lensu(
    get = (_:VisitState).pv,
    set = (msg:VisitState, pv1:Option[PV1Segment]) => msg.copy(pv = pv1))

  val eventOccuredLens: Lens[EVNSegment, Option[String]] = Lens.lensu(
    get = (_:EVNSegment).evnOccured,
    set = (evn:EVNSegment, s:Option[String]) => evn.copy(evnOccured = s)
  )
  val evnMsgLens: Lens[ADTMsg, EVNSegment] = Lens.lensu(
    get = (_:ADTMsg).evn,
    set = (msg:ADTMsg, e:EVNSegment) => msg.copy(evn = e)
  )

  val familyName = pidVisitStateLens >=> pidPatientLens >=> patientFamilyNameLens

  val consultingDoctorL = pv1VisitStateLens >=> consultingDoctorLens

  val eventOccured = evnMsgLens >=> eventOccuredLens

  val admitDate = pv1VisitStateLens >=> admitDatePV1Lens

  val wardCode = pv1VisitStateLens >=> wardCodePV1Lens

  val wardName = pv1VisitStateLens >=> wardNamePV1Lens

  val dischargeDate = pv1VisitStateLens >=> dischargeDatePV1Lens
  
  def exclude[A](n:A*)(implicit l:List[A]) : List[A] =  l.filter(a => ! (n contains a))

  implicit val allEvents = Event.AllEvents

  def nextEvent =  {
    import Status._
    for {
      s <- get[VisitState].lift[Gen]
      _ <- modify[VisitState](s => s.status match {
        case Unknown => eventLens.set(s,CreatePatientEvent)
        case Known => eventLens.set(s,Random.shuffle(List(AdmitEvent, UpdatePersonEvent)).headOption | NothingEvent)
        case Admitted => eventLens.set(s,Random.shuffle(exclude(AdmitEvent, CancelDischargeEvent, CancelTransferEvent, CreatePatientEvent,NothingEvent)).headOption | NothingEvent)
        case Transferred => eventLens.set(s,Random.shuffle(exclude(AdmitEvent, CancelDischargeEvent, CreatePatientEvent,NothingEvent)).headOption | NothingEvent)
        case Discharged => eventLens.set(s,CancelDischargeEvent)
        case Finished => eventLens.set(s,NothingEvent)
      }).lift[Gen]
    } yield ()
  }


  def nextState = {
    for {
      s <- get[VisitState].lift[Gen]
      _ <- modify[VisitState](s => s.event match {
        case CreatePatientEvent => stateLens.set(s,Status.Known)
        case DischargeEvent => stateLens.set(s,Status.Discharged)
        case AdmitEvent => stateLens.set(s,Status.Admitted)
        case TransferEvent => stateLens.set(s,Status.Transferred)
        case CancelAdmitEvent => stateLens.set(s,Status.Finished)
        case CancelTransferEvent => stateLens.set(s,Status.Admitted)
        case CancelDischargeEvent => stateLens.set(s,Status.Admitted)
        case a => s
      }).lift[Gen]
    } yield ()
  }


  def addpv1: State[VisitState,VisitState]= {
    for {
      _ <- pv1VisitStateLens := PV1(DateTime.now.some,None).sample
      s <- get
    } yield s

  }

  def step =  for {
      s <- get[VisitState].lift[Gen]
      _ <- nextEvent
      _ <- nextState
      _ <- updatePV1
      _ <- updatePID
      n <- get[VisitState].lift[Gen]
      m <- StateT[Gen,VisitState,ADTMsg](s => createMsg(s).map(s->_))
    } yield m

  def initState: Gen[VisitState] = for {
      pid <- PID
      pv1 <- PV1(None,None)
      state <- Gen.const(AdmitEvent)
      ss <- Gen.const(VisitState(state, None, pid,Status.Unknown))
    } yield ss

  def createVisit : Gen[List[ADTMsg]] = {
    val list:Gen[List[ADTMsg]] = for {
      ss <- initState
      l <- Gen.choose(3,20)
      x <- step.replicateM(l).eval(ss)
    } yield x.takeWhile(_.msh.msgType != "XXX")


    list.map(l => {
      l.map(m => {
        if(m.msh.msgType == "A08") {
          //grab all msg previous to the a08 and shuffle them
          val r = Random.shuffle(l.takeWhile(_ != m))
            //find the first A01/02/03
            .find(_.msh.msgType |>  List("A01","A02","A03").contains)
            //set this msgs evnOccured to the found one
            .map(s => eventOccured.set(m,s.evn.evnOccured))
          r | m
        }
        else m
      })
    })
  }

  case class Patient(hospitalNo:String,nhsNumber:Option[String],givenName: Option[String], middleNames: Option[String], familyName: Option[String], title: Option[String], mothersMaidenName: Option[String], dob: Option[DateTime], sex: Option[String], streetAddress: Option[String], city: Option[String], state: Option[String], zip: Option[String], country: Option[String], phone: Option[String])

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

  case class PV1Segment(wardCode:Option[String],bed:Option[Int],wardName:Option[String],consultingDoctor:Option[Doctor],referringDoctor:Doctor,admittingDoctor:Doctor,hospitalService:Option[String],patientType:Option[String],visitID:String,admitDate:Option[DateTime],dischargeDate:Option[DateTime]){
    override def toString:String =
    s"PV1|1|I|${~wardCode}^^${bed.map(_.toString) | ""}^^^^^^${~wardName}|11||||${referringDoctor.id}^${referringDoctor.familyName}^${referringDoctor.givenName}^${referringDoctor.middleNames}^^${referringDoctor.title}|" +
      s"${~consultingDoctor.map(_.id)}^${~consultingDoctor.map(_.familyName)}^${~consultingDoctor.map(_.givenName)}^${~consultingDoctor.map(_.middleNames)}^^${~consultingDoctor.map(_.title)}|${~hospitalService}|||||||" +
      s"${admittingDoctor.id}^${admittingDoctor.familyName}^${admittingDoctor.givenName}^${admittingDoctor.middleNames}^^${admittingDoctor.title}|${~patientType}|$visitID|||||||" +
      s"||||||||||||||||||${~admitDate.map(_.toString(toDateTimeFormat))}|${~dischargeDate.map(_.toString(toDateTimeFormat))}"
  }

  case class ADTMsg(msh:MSHSegment,evn:EVNSegment,pid:PIDSegment,pv1:Option[PV1Segment]){
    override def toString:String =
    s"$msh\r$evn\r$pid\r${~pv1.map(_.toString)}"
  }

  val fromDateTimeFormat = DateTimeFormat.forPattern("MM/dd/yyyy")
  val toDateTimeFormat = DateTimeFormat.forPattern("yyyyMMddHHmmss")
  val oerpDateTimeFormat = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")

  val patients = Source.fromFile("src/test/resources/fake_patients.csv").getLines()
  val doctors = Source.fromFile("src/test/resources/fake_doctors.csv").getLines()

  val hospitalServices = List("110", "160")
  val patientTypes = List("01")

  val wards = Map("WARD A" -> "A", "WARD B" -> "B")


  val updatePID:StateT[Gen,VisitState,Unit] =
  for {
    _ <- StateT[Gen,VisitState,Unit](s => Gen.frequency(
      (5, Gen.const(s -> ())),
      (1, Gen.listOfN(10,Gen.alphaChar).map(n => familyName.set(s,Some(n.mkString)) -> ()))
    ))
  } yield ()

  type PV1State[A] = StateT[Gen, PV1Segment, A]


  val updatePV1:StateT[Gen,VisitState, Unit] =
    for {
      _ <- StateT[Gen,VisitState, Unit](s => Gen.frequency(
        (5,Gen.const(s -> ())),
        (1, Gen.option(genDoctor).map((d:Option[Doctor]) => consultingDoctorL.set(s, d) -> ()))

      ))
      _ <- StateT[Gen,VisitState,Unit](s => Gen.frequency(
        (5, Gen.const(s -> ())),
        (1, Gen.option(genWard).map((w:Option[String]) => wardCode.set(s,w) -> ()))
      ))
      _ <- StateT[Gen,VisitState,Unit](s =>
        if(s.event == AdmitEvent && s.pv.isEmpty)
          PV1(DateTime.now.some,None).map(p => pv1VisitStateLens.set(s,p.some) -> ())
        else
          s->())
    } yield ()


  def genWard : Gen[String] = Gen.oneOf(wards.keys.toList).flatMap(wards(_))




  val createMsg: (VisitState) => Gen[ADTMsg] = { (s:VisitState) =>
   val msgType = s.event match{
      case NothingEvent => "XXX"
      case AdmitEvent => "A01"
      case DischargeEvent => "A03"
      case TransferEvent =>"A02"
      case CancelAdmitEvent => "A11"
      case CancelDischargeEvent =>"A13"
      case CancelTransferEvent => "A12"
      case UpdatePatientEvent => "A08"
      case UpdatePersonEvent => "A31"
      case CreatePatientEvent => "A28"
      case MergePatientsEvent => "A40"
    }
    genMsg(msgType, s)
  }

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
      fn <- Gen.listOfN(10,Gen.alphaNumChar).map(_.mkString) // put family name back as optional once bug fixed in OERP
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
    } yield Patient(hospitalNum, nhsNum, gn,mn,Some(fn),None,mm,dob.map(d => DateTime.parse(d,fromDateTimeFormat)),s.flatMap(_.headOption.map(_.toString)),street,city,state,zip,country,phone)


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
     genPatient.flatMap(PIDSegment(_))
  }

  def PV1(admitDate: Option[DateTime], dischargeDate: Option[DateTime]): Gen[PV1Segment] = {
    for {
      wardName <- Gen.oneOf(wards.keys.toList)
      wardCode = wards(wardName)
      consultingDoctor <- Gen.option(genDoctor)
      referringDoctor <- genDoctor
      admittingDoctor <- genDoctor
      hospitalService <- Gen.option(Gen.oneOf(hospitalServices))
      patientType <- Gen.option(Gen.oneOf(patientTypes))
      visitID <- Gen.listOfN(25, Gen.alphaNumChar).map(_.mkString)
    } yield PV1Segment(Some(wardCode),None,Some(wardName),consultingDoctor,referringDoctor,admittingDoctor,hospitalService,patientType,visitID,admitDate,dischargeDate)
  }

}