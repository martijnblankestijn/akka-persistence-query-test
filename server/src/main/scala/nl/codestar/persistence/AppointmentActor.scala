package nl.codestar.persistence

import java.time.LocalDateTime
import java.util.UUID

import akka.Done
import akka.actor.SupervisorStrategy.Stop
import akka.actor.{ActorLogging, Props, ReceiveTimeout}
import akka.cluster.ddata.Replicator.Get
import akka.persistence.{PersistentActor, RecoveryCompleted}

import scala.concurrent.duration._
import nl.codestar.domain.domain.{Cancelled, State, Tentative}
import nl.codestar.persistence.AppointmentActor._
import nl.codestar.persistence.Validations.{Validation, validateInTheFuture}
import nl.codestar.persistence.events.{AppointmentCancelled, AppointmentEvent, AppointmentMoved, AppointmentReassigned, _}

import scala.concurrent.duration.FiniteDuration
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.Passivate

class AppointmentActor extends PersistentActor with ActorLogging {
  override val persistenceId: String = {
    val name = self.path.name
    log.info(s"Created new persistence actor with id [$name]")
    name
  }
  
  var state: Appointment = _

  // passivate the entity when no activity
  context.setReceiveTimeout(120.seconds)
  
  def updateState(event:AppointmentEvent) :Unit = 
    state = event match {
      case AppointmentCreated(advisorId, r, s, d, b) =>
        context.become(created())
        Appointment( state = Tentative, branchId = b, advisorId = advisorId, roomId = r, start = s, duration = d)

      case AppointmentReassigned(advisorId) => state.copy(advisorId = advisorId)

      case AppointmentCancelled(reason) =>
        context.become(cancelled())
        state.copy(state = Cancelled)

      case AppointmentMoved(advisorId, roomId, startDateTime, branchId) =>
        state.copy(advisorId = advisorId,roomId = roomId, start = startDateTime, branchId = branchId)
    }
  

  override def receiveCommand: Receive = initializing()

  def initializing(): Receive = {
    case GetDetails(uuid) =>  sender() ! GetDetailsResult(None)

    case command: CreateAppointment =>
      val event: Validation[AppointmentCreated] = validateAndCreateEvent(command)
      
      event.bimap(
        errors => sender() ! CommandFailed(errors toList),
        event  => persist(event)(updateState _ andThen( _ => sender() ! UUID.fromString(persistenceId)))
    )
  }


  def created(): Receive = {
    case GetDetails(uuid)                => sender() ! GetDetailsResult(Some(state))

    case ReassignAppointment(appointmentId, uuid) => persist(AppointmentReassigned(uuid))(updateState _ andThen sendDone)

    case CancelAppointment(uuid)         => persist(AppointmentCancelled())(updateState _ andThen sendDone)

    case MoveAppointment(appointmentId, branchId, advisor, room, start) =>
      val moved = AppointmentMoved(advisor.toString, room.map(_.toString).orNull, Some(start), branchId)
      log.info(s"MOVE $moved")
      persist(moved)(updateState _ andThen sendDone)
  }

  def sendDone: Any => Unit = _ => sender ! Done
  
  def cancelled(): Receive = {
    case GetDetails(uuid) => sender() ! GetDetailsResult(None)
  }

  override def unhandled(message: Any): Unit = {
    message match {
      // what does this do exactly and why??
      case ReceiveTimeout => context.parent ! Passivate(stopMessage = Stop)
      case Passivate(Stop) => context.stop(self)
      case rc: RecoveryCompleted => log.info(s"Recovery complete for $persistenceId")
      case _ => super.unhandled(message)
    }
    
  }

  override def receiveRecover: Receive = {
    case x: AppointmentEvent =>
      log.debug(s"Recovering ... $x")
      updateState(x)
  }
}

object AppointmentActor {
  def props() = Props(new AppointmentActor)
  def name(uuid: UUID) = uuid.toString

  // state of the persistent entity
  case class Appointment(state: State = Tentative, branchId: UUID, advisorId: UUID, roomId: Option[UUID], start: LocalDateTime, duration: FiniteDuration)

  // protocol
  // commands - external stimuli which do not get persisted
  trait Command {
    def appointmentId: UUID
  }

  case class CreateAppointment(appointmentId: UUID, branchId: UUID, advisorId: UUID, room: Option[UUID], start: LocalDateTime, duration: FiniteDuration) extends Command
  case class ReassignAppointment(appointmentId: UUID, advisorId: UUID) extends Command
  case class MoveAppointment(appointmentId: UUID, branchId: UUID, advisorId: UUID, roomId: Option[UUID], start: LocalDateTime) extends Command
  case class CancelAppointment(appointmentId: UUID) extends Command
  case class GetDetails(appointmentId: UUID) extends Command
  // results
  case class GetDetailsResult(value: Option[Appointment])
  case class CommandFailed(errors: List[Error])
  
  private def validateAndCreateEvent(c: CreateAppointment): Validation[AppointmentCreated] = {
    validateInTheFuture(c.start)
      .map(start =>
        AppointmentCreated(c.advisorId, c.room, Some(start), Some(c.duration), c.branchId.toString))

  }

  
  // 
  // SHARDING
  //
  val shardName = "appointments"
  val numberOfShards = 100

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case a : Command ⇒ (a.appointmentId.toString, a)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    case a : Command ⇒ (a.appointmentId.hashCode() % numberOfShards).toString
  }

}



