package com.tactix4.t4ADT

import com.tactix4.t4ADT
import scalaz._
import Scalaz._
import org.scalacheck.Gen

/**
 * Created by max on 03/06/14.
 */

sealed trait Event
case object NothingEvent extends Event
case object AdmitEvent extends Event
case object DischargeEvent extends Event
case object TransferEvent extends Event
case object CancelAdmitEvent extends Event
case object CancelDischargeEvent extends Event
case object CancelTransferEvent extends Event
case object UpdatePatientEvent extends Event
case object CreatePatientEvent extends Event
case object MergePatientsEvent extends Event

sealed trait ADTState {
  val transitions: Map[Event, ADTState]
}

case object VisitEnd extends ADTState {
  val transitions:Map[Event,ADTState] = Map(
    NothingEvent -> VisitEnd
  )
}

case object Start extends ADTState {
  val transitions: Map[Event, ADTState] = Map(
    AdmitEvent -> Admitted,
    TransferEvent -> Admitted,
    DischargeEvent -> Discharged,
    CancelAdmitEvent -> VisitEnd,
    CancelTransferEvent -> Admitted,
    CancelDischargeEvent -> Admitted,
    UpdatePatientEvent -> Admitted,
    MergePatientsEvent -> Admitted,
    CreatePatientEvent -> PatientCreated
  )
}


case object PatientCreated extends ADTState{
  override val transitions: Map[Event, ADTState] = Map[Event,ADTState](
    AdmitEvent -> Admitted,
    NothingEvent -> VisitEnd
  )
}

case object Admitted extends ADTState {
  val transitions = Map[Event, ADTState](
    TransferEvent -> Admitted,
    DischargeEvent -> Discharged,
    CancelAdmitEvent -> VisitEnd,
    CancelTransferEvent -> Admitted,
    UpdatePatientEvent -> Admitted,
    MergePatientsEvent -> Admitted)
}

case object Discharged extends ADTState {
  val transitions: Map[Event, ADTState] = Map(
    CancelDischargeEvent -> Admitted
  )
}

trait VisitGen {


  val visitGenerator = StateT[Gen, ADTState, Event]((s: ADTState) => Gen.oneOf(s.transitions.toSeq).map(_.swap))


  import org.scalacheck.{Gen, Arbitrary, Shrink}
  import Gen.{sized, value}

  implicit val ArbitraryMonad: Monad[Arbitrary] = new Monad[Arbitrary] {
    def bind[A, B](fa: Arbitrary[A])(f: A => Arbitrary[B]) = Arbitrary(fa.arbitrary.flatMap(f(_).arbitrary))
    def point[A](a: => A) = Arbitrary(sized(_ => Gen.const(a)))
    override def map[A, B](fa: Arbitrary[A])(f: A => B) = Arbitrary(fa.arbitrary.map(f))
  }

  implicit val GenMonad: Monad[Gen] = new Monad[Gen] {
    def point[A](a: => A) = sized(_ => Gen.const(a))
    def bind[A, B](fa: Gen[A])(f: A => Gen[B]) = fa flatMap f
    override def map[A, B](fa: Gen[A])(f: A => B) = fa map f
  }
}