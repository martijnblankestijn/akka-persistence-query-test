package nl.codestar.persistence

import java.time.LocalDateTime
import java.util.UUID

import akka.Done
import akka.actor.SupervisorStrategy.Stop
import akka.actor.{ActorLogging, ActorRef, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.journal.Tagged
import akka.persistence.{PersistentActor, RecoveryCompleted}
import cats.implicits._
import nl.codestar.domain.domain.{Cancelled, State, Tentative}
import nl.codestar.persistence.AppointmentActor._
import nl.codestar.persistence.Validations.{Validation, validateDuration, validateInTheFuture}
import nl.codestar.persistence.events.{AppointmentCancelled, AppointmentEvent, AppointmentMoved, AppointmentReassigned, _}
import nl.codestar.persistence.phantom.AppointmentReadSide

import scala.collection.immutable.Seq
import scala.concurrent.duration.{FiniteDuration, _}

class AppointmentActor extends PersistentActor with ActorLogging {
  override val persistenceId: String = {
    val name = self.path.name
    log.debug("Created new persistent actor with id [{}]", name)
    name
  }
  
  var state: Appointment = _

  // passivate the entity when no activity
  context.setReceiveTimeout(120.seconds)
  
  def updateState(event:AppointmentEvent) :Unit = {
    log.debug("Updating state with {}", event)
    state = event match {
      case AppointmentCreated(advisorId, roomId, start, duration, branchId) =>
        context.become(created())
        Appointment( state = Tentative, start = start, duration = duration, 
          branchId = branchId, advisorId = advisorId, roomId = roomId)

      case AppointmentReassigned(advisorId) => state.copy(advisorId = advisorId)

      case AppointmentCancelled(reason) =>
        context.become(cancelled())
        state.copy(state = Cancelled)

      case AppointmentMoved(advisorId, roomId, startDateTime, branchId) =>
        state.copy(advisorId = advisorId,roomId = roomId, start = startDateTime, branchId = branchId)
    }
  }

  override def receiveCommand: Receive = initializing()

  def persistReadShardedEvent(event: AppointmentEvent)(handler: AppointmentEvent ⇒ Unit): Unit = {
    val taggedEvent: Tagged = tag(event)
    super.persist(taggedEvent) { evt: Tagged => handler(evt.payload.asInstanceOf[AppointmentEvent]) }
  }
  
  private def tag(event: AppointmentEvent): Tagged = {
    Tagged(event, Set(AppointmentReadSide.createReadSideShardId(persistenceId)))
  }
  
  def initializing(): Receive = {
    case GetDetails(uuid) =>  sender() ! GetDetailsResult(None)

    case command: CreateAppointment =>
      def replyToSender: Unit = {
        log.info("Reply to {} with {}", sender(), persistenceId)
        sender() ! persistenceId
      }
      log.info(s"Got message $command from ${sender()}")
      val event: Validation[AppointmentCreated] = validateAndCreateEvent(command)
      event.bimap(
        errors => sender() ! CommandFailed(errors toList),
        event  => persistReadShardedEvent(event){ evt =>
          updateState(evt)
          replyToSender
        }
    )
  }


  def created(): Receive = {
    case GetDetails(id)                     =>
      log.debug(s"Return state $state of $id to ${sender()}")
      sender() ! GetDetailsResult(Some(state))

    case ReassignAppointment(id, advisorId) => persistReadShardedEvent(AppointmentReassigned(advisorId))(updateState _ andThen sendDone)

    case CancelAppointment(id)              => persistReadShardedEvent(AppointmentCancelled())(updateState _ andThen sendDone)

    case MoveAppointment(id, branchId, advisor, room, start) =>
      val moved = AppointmentMoved(advisor.toString, room.map(_.toString).orNull, Some(start), branchId)
      persistReadShardedEvent(moved)(updateState _ andThen sendDone)

    case c: CreateAppointment               =>
      log.warning(s"Apppointment ${c.appointmentId} already created")
      sender ! CommandFailed(List(AlreadyExists("Appointment already exists", Seq(c.appointmentId))))
  }

  def sendDone: Any => Unit = _ => sender ! Done
  
  def cancelled(): Receive = {
    case GetDetails(id) => sender() ! GetDetailsResult(None)
    case c: Command     =>  sender ! CommandFailed(List(CancelledError("Appointment already cancelled", Seq(c.appointmentId))))
  }

  override def unhandled(message: Any): Unit = {
    message match {
      // what does this do exactly and why??
      case ReceiveTimeout => context.parent ! Passivate(stopMessage = Stop)
      case Passivate(Stop) => context.stop(self)
      case _: RecoveryCompleted => log.debug("Recovery complete for {}" , persistenceId)
      case _ => super.unhandled(message)
    }
    
  }

  override def receiveRecover: Receive = {
    case event: AppointmentEvent =>
      log.debug(s"Recovering ... {}", event)
      updateState(event)
  }
}

object AppointmentActor {
  def props(): Props = Props(new AppointmentActor)
  def name(id: UUID): String = id.toString

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
    (validateInTheFuture(c.start), validateDuration(c.duration))
      .mapN( (start, duration) =>
        AppointmentCreated(c.advisorId, c.room, Some(start), Some(duration), c.branchId.toString))
  }

  
  // 
  // SHARDING FOR CLUSTERING
  //
  val shardName = "appointments"
  val numberOfShards = 100

  // Partial function to extract the entity id PF van msg => (id, msg)
  val extractEntityId: ShardRegion.ExtractEntityId = {
    case a : Command ⇒ (a.appointmentId.toString, a)
  }

  // Partial function to extract the shard if from the message
  val extractShardId: ShardRegion.ExtractShardId = {
    case a : Command ⇒ (a.appointmentId.hashCode() % numberOfShards).toString
  }
}



