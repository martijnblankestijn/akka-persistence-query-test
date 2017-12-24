package nl.codestar.endpoints

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{HttpResponse, headers}
import akka.http.scaladsl.server.Directives.{
  as,
  complete,
  entity,
  get,
  path,
  post,
  _
}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import nl.codestar.appointments.AppointmentActor
import nl.codestar.appointments.AppointmentActor._
import nl.codestar.persistence.phantom.{Appointment, AppointmentsDatabase}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait AppointmentEndpoint extends JsonProtocol {
  implicit def system: ActorSystem
  implicit def executionContext: ExecutionContext
  implicit val timeout = Timeout(5 seconds)

  val route: Route =
    pathPrefix("appointments") {
      pathPrefix(JavaUUID) { uuid =>
        get { getAppointment(uuid) } ~
          delete { deleteAppointment(uuid) } ~
          path("reassign") { post { reassign(uuid) } } ~
          path("move") { post { move(uuid) } }
      } ~
        pathEnd {
          get { getAllAppointments } ~
            post { createAppointment }
        }
    }

  private def move(uuid: UUID) = entity(as[MoveAppointment]) { move =>
    complete {
      logger.debug("Moving appointment {} to {}", uuid, move)
      (calendar ? move) map (_ => NoContent)
    }
  }

  private def reassign(uuid: UUID) = entity(as[ReassignAppointment]) {
    reassign =>
      complete {
        logger.debug("Reassigning appointment {} to {}", uuid, reassign)
        (calendar ? reassign) map (_ => NoContent)
      }
  }

  private def deleteAppointment(uuid: UUID) = complete {
    logger.debug(s"Deleting {}", uuid)
    (calendar ? AppointmentActor.CancelAppointment(uuid)) map (_ => NoContent)
  }

  private def getAppointment(uuid: UUID) =
    rejectEmptyResponse { // turns the result Option[T] to a 404 response
      complete {
        logger.debug(s"Get result for {}", uuid)
        //        AppointmentsDatabase.appointments.getById(uuid) // alternative to the find appointment
        (calendar ? GetDetails(uuid))
          .mapTo[AppointmentActor.GetDetailsResult]
          .map(
            _.value.map(
              r =>
                Appointment(uuid,
                            r.branchId,
                            r.state.toString,
                            r.advisorId,
                            r.roomId,
                            r.start)))
      }
    }

  private def getAllAppointments =
    complete(AppointmentsDatabase.appointments.getAll())

  private def createAppointment = entity(as[CreateAppointment]) { create =>
    extractUri { uri =>
      complete {
        logger.debug("Post new appointment with id {} at {} with {} in room {}",
                     create.appointmentId,
                     create.start,
                     create.advisorId,
                     create.room)

        val future: Future[Any] = calendar ? create
        future collect {
          case persistentActorId: String =>
            logger.debug("Got reply from persistent actor {} with id {}",
                         persistentActorId,
                         create.appointmentId)
            val locationHeader = headers.Location(
              uri withPath (uri.path / create.appointmentId.toString))
            HttpResponse(Created, headers = List(locationHeader))

          case CommandFailed(err) =>
            logger.info("Command failed {}", err)
            HttpResponse(BadRequest, entity = err.toString())

          case x =>
            logger.error("Why is someone sending me {}", x)
            // never do this, as this could expose information
            HttpResponse(InternalServerError, entity = s"Why did I get $x")
        }
      }
    }
  }

  def logger: LoggingAdapter

  def calendar: ActorRef
}
