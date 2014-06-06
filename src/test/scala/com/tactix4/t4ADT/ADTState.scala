package com.tactix4.t4ADT

import com.tactix4.t4ADT

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
    MergePatientsEvent -> Admitted
  )
}

case object Unadmitted extends ADTState {
  val transitions = Map[Event,ADTState](
    AdmitEvent -> Admitted,
    CreatePatientEvent -> PatientCreated,
    UpdatePatientEvent -> PatientCreated
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
  import scala.util.Random
  val visitGenerator = scalaz.State[ADTState, Event]((s: ADTState) => Random.shuffle(s.transitions).head.swap)
}