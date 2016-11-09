package nl.codestar.persistence

import java.time.LocalDateTime
import java.util.UUID.randomUUID

import akka.actor.ActorSystem
import akka.pattern._
import akka.util.Timeout
import nl.codestar.persistence.AppointmentActor.{Appointment, ReassignAppointment}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object Server extends App {
  implicit val executionContext = ExecutionContext.Implicits.global
  implicit val timeout = Timeout(2.seconds)
  
  private def printDetails() = {
    (persistentActor ? AppointmentActor.GetDetails).mapTo[Appointment].foreach(println)
  }

  val system = ActorSystem("example")
  private val advisor = randomUUID()
  
  val persistentActor = system.actorOf(AppointmentActor.props(randomUUID(), advisor, None, LocalDateTime.now, 30.minutes))
  private val newAdvisor = randomUUID()

  printDetails()

  persistentActor ! ReassignAppointment(newAdvisor)

  printDetails()

  persistentActor ! AppointmentActor.CancelAppointment

  printDetails()

  Thread.sleep(10000)
  system.terminate()
}
