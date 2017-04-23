package nl.codestar.persistence

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import nl.codestar.endpoints.{AppointmentEndpoint, JsonProtocol}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._


object Server extends App with JsonProtocol with AppointmentEndpoint {
  implicit val system = ActorSystem("appointmentSystem")
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher // needed for map/flatMap of future
  implicit val materializer = ActorMaterializer()
  val logger = Logging(system, getClass)

  override val calendar = system.actorOf(CalendarActor.props(), "appointments")
  implicit override val timeout = Timeout(2.seconds)

  val host = "localhost"
  val port = 8080

  Http().bindAndHandle(route, host, port) map { binding =>
    logger.info(s"REST interface bound to ${binding.localAddress}")
  } recover { case ex =>
    logger.error(s"REST interface could not bind to $host:$port", ex.getMessage)
  }
}
