package nl.codestar.persistence

import java.time.LocalDateTime
import java.util.UUID

import akka.Done
import akka.actor.{ActorLogging, Props}
import akka.persistence.PersistentActor
import nl.codestar.domain.domain.{Cancelled, State, Tentative}
import nl.codestar.persistence.AppointmentActor._
import nl.codestar.persistence.Validations.{Validation, validateInTheFuture}
import nl.codestar.persistence.events.{AppointmentCancelled, AppointmentEvent, AppointmentMoved, AppointmentReassigned, _}

import scala.concurrent.duration.FiniteDuration


class AppointmentActor(id: UUID) extends PersistentActor with ActorLogging {
  override val persistenceId: String = id.toString
  var state: Appointment = _

  def updateState(event:AppointmentEvent) :Unit = 
    state = event match {
      case AppointmentCreated(a, r, s, d, b) =>
        context.become(created())
        Appointment( state = Tentative, branchId = b, advisorId = a, roomId = r, start = s, duration = d)

      case AppointmentReassigned(advisorId) => state.copy(advisorId = advisorId)

      case AppointmentCancelled(reason) =>
        context.become(cancelled())
        state.copy(state = Cancelled)

      case AppointmentMoved(advisorId, roomId, startDateTime, branchId) =>
        state.copy(advisorId = advisorId,roomId = roomId, start = startDateTime, branchId = branchId)
    }
  

  override def receiveCommand: Receive = initializing()

  def initializing(): Receive = {
    case GetDetails =>  sender() ! None

    case command: CreateAppointment =>
      val event: Validation[AppointmentCreated] = validateAndCreateEvent(command)
      
      event.bimap(
        errors => sender() ! CommandFailed(errors toList),
        event  => persist(event)(updateState _ andThen( _ => sender() ! id))
    )
  }


  def created(): Receive = {
    case GetDetails                => sender() ! GetDetailsResult(Some(state))

    case ReassignAppointment(uuid) => persist(AppointmentReassigned(uuid))(updateState _ andThen sendDone)

    case CancelAppointment         => persist(AppointmentCancelled())(updateState _ andThen sendDone)

    case MoveAppointment(branchId, advisor, room, start) =>
      val moved = AppointmentMoved(advisor.toString, room.map(_.toString).orNull, Some(start), branchId)
      log.info(s"MOVE $moved")
      persist(moved)(updateState _ andThen sendDone)
  }

  def sendDone: Any => Unit = _ => sender ! Done
  
  def cancelled(): Receive = {
    case GetDetails => sender() ! GetDetailsResult(None)
  }

  override def receiveRecover: Receive = {
    case x: AppointmentEvent =>
      log.debug(s"Recovering ... $x")
      updateState(x)
  }
}


object AppointmentActor {
  def props(id: UUID): Props = Props(new AppointmentActor(id))

  // state of the persistent entity
  case class Appointment(state: State = Tentative, branchId: UUID, advisorId: UUID, roomId: Option[UUID], start: LocalDateTime, duration: FiniteDuration)

  // protocol
  // commands
  trait Command
  case class CreateAppointment(branchId: UUID, advisorId: UUID, room: Option[UUID], start: LocalDateTime, duration: FiniteDuration) extends Command
  case class ReassignAppointment(advisorId: UUID) extends Command
  case class MoveAppointment(branchId: UUID, advisorId: UUID, roomId: Option[UUID], start: LocalDateTime) extends Command
  case object CancelAppointment extends Command
  case object GetDetails extends Command
  // results
  case class GetDetailsResult(value: Option[Appointment]) extends Command
  
  case class CommandFailed(errors: List[Error])
  


  private def validateAndCreateEvent(c: CreateAppointment): Validation[AppointmentCreated] = {
    validateInTheFuture(c.start)
      .map(start =>
        AppointmentCreated(c.advisorId, c.room, Some(start), Some(c.duration), c.branchId.toString))

  }
}



