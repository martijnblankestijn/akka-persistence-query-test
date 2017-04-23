package nl.codestar.persistence

import java.util.UUID
import java.util.UUID.randomUUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import nl.codestar.persistence.AppointmentActor._
import nl.codestar.persistence.CalendarActor.{CancelAppointment, FindAppointment}

class CalendarActor extends Actor with ActorLogging {
  override def receive: Receive = {
    case c: CreateAppointment =>                  forward(randomUUID, c)
    case FindAppointment(uuid) =>                 forward(uuid, GetDetails)
    case CancelAppointment(uuid) =>               forward(uuid, AppointmentActor.CancelAppointment)
    case (uuid: UUID, ra: ReassignAppointment) => forward(uuid, ra)  
    case (uuid: UUID, move: MoveAppointment) =>   forward(uuid, move)  
      
  }

  private def forward(uuid: UUID, command: AppointmentActor.Command) = {
    log.debug(s"Forwarding $command to appointment $uuid")
    getChild(uuid)  forward command
  }

  private def getChild(uuid: UUID): ActorRef = 
    context.child(createAppointmentName(uuid))
      .getOrElse(context.actorOf(AppointmentActor.props(uuid), createAppointmentName(uuid)))

  private def createAppointmentName(appointmentId: UUID) = s"appointment-$appointmentId"
}

object CalendarActor {
  def props() = Props(new CalendarActor)

  // protocol
  case class CancelAppointment(uuid: UUID)
  case class FindAppointment(uuid: UUID)

}