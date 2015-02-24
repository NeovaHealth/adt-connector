package uk.co.neovahealth.nhADT

import org.scalacheck.Gen

import scalaz._

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
case object UpdatePersonEvent extends Event
case object CreatePatientEvent extends Event
case object MergePatientsEvent extends Event

object Event {
  val AllEvents = List(NothingEvent, AdmitEvent, DischargeEvent,TransferEvent,CancelAdmitEvent,
                               CancelTransferEvent,CancelDischargeEvent,UpdatePatientEvent,UpdatePersonEvent,
                               CreatePatientEvent,MergePatientsEvent)
}


trait VisitGen {


//  val visitGenerator = StateT[Gen, ADTAspect, Event]((s: ADTAspect) => Gen.oneOf(s.transitions.toSeq).map(_.swap))


  import org.scalacheck.Gen.sized
  import org.scalacheck.{Arbitrary, Gen}

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