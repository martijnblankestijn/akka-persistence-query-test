import java.util.UUID
import java.util.UUID.randomUUID

import akka.actor.ActorSystem
import akka.pattern._
import akka.util.Timeout
import nl.codestar.persistence.AppointmentActor
import nl.codestar.persistence.AppointmentActor.Appointment

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object Client extends App {
  implicit val executionContext = ExecutionContext.Implicits.global
  implicit val timeout = Timeout(2.seconds)
  
  private def printDetails() = {
    (persistentActor ? AppointmentActor.GetDetails).mapTo[nl.codestar.query.phantom.Appointment].foreach(println)
  }

  val system = ActorSystem("example")
  private val advisor = randomUUID()
  
  val persistentActor = system.actorOf(AppointmentActor.props(UUID.fromString("b90e960f-6ae6-44fe-bb15-2195928856ca")))
  printDetails()
  Thread.sleep(10000)
  system.terminate()
}
