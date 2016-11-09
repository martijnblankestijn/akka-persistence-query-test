package nl.codestar.persistence

import java.time.LocalDateTime
import java.util.UUID

import akka.actor.{ActorLogging, Props}
import akka.persistence.PersistentActor
import nl.codestar.persistence.AppointmentActor._

import scala.concurrent.duration.FiniteDuration

class AppointmentActor(id: UUID, advisor: UUID, room: Option[UUID], start: LocalDateTime, duration: FiniteDuration) extends PersistentActor with ActorLogging {
  // events
  trait Event
  case object Cancel extends Event
  case class Reassign(advisor: UUID) extends Event
  case class Move(advisor: UUID, room: Option[UUID], start: LocalDateTime) extends Event


  override val persistenceId: String = id.toString
  var state: Appointment = Appointment(advisor = advisor, room = room, start = start, duration = duration)

  override def receiveRecover: Receive = {
    case x : Event => updateState(x)
  }

  override def receiveCommand: Receive = {
    case GetDetails => sender() ! state
      
    case ReassignAppointment(uuid) => 
      log.debug(s"Reassigning the appointment $uuid to advisor $uuid")
      persist(Reassign(uuid)) (updateState)

    case CancelAppointment =>
      log.debug(s"Cancel appointment $id")
      persist(Cancel)(updateState)
    
    case MoveAppointment(advisor, room, start) =>
      persist(Move(advisor, room, start))(updateState)
  }

  val updateState: Event => Unit = {
    case Reassign(a) => state = state.copy(advisor = a)
    case Cancel => state = state.copy(state = Cancelled)
    case Move(a, r, s) => state.copy(advisor = a, room = r, start = s)
  }
}


object AppointmentActor {
  def props(id: UUID, advisor: UUID, room: Option[UUID], start: LocalDateTime, duration: FiniteDuration): Props =
    Props(new AppointmentActor(id, advisor, room, start, duration))



  // protocol
  trait Command
  case class ReassignAppointment(advisor:UUID) extends Command
  case class MoveAppointment(advisor: UUID, room: Option[UUID], start: LocalDateTime) extends Command
  case object CancelAppointment extends Command
  case object GetDetails extends Command


  case class Appointment(state: State = Tentative, advisor: UUID, room: Option[UUID], start: LocalDateTime, duration: FiniteDuration)
  // states
  trait State
  case object Busy extends State
  case object Tentative extends State
  case object Confirmed extends State
  case object Cancelled extends State
}



