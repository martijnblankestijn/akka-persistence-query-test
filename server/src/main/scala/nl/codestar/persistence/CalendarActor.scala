package nl.codestar.persistence

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import nl.codestar.persistence.AppointmentActor.Command

class CalendarActor extends Actor with ActorLogging {
  override def receive: Receive = {
    case x: Command => forward(x.appointmentId, x)
  }

  private def forward(uuid: UUID, command: Command) = {
    log.debug(s"Forwarding $command to appointment $uuid")
    getChild(uuid)  forward command
  }

  private def getChild(uuid: UUID): ActorRef = 
    context.child(AppointmentActor.name(uuid))
      .getOrElse(context.actorOf(AppointmentActor.props(), AppointmentActor.name(uuid)))
}

object CalendarActor {
  def props() = Props(new CalendarActor)
}