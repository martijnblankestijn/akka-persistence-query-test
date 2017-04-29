package nl.codestar.endpoints

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes.{BadRequest, Created, NoContent}
import akka.http.scaladsl.model.{HttpResponse, StatusCodes, headers}
import akka.http.scaladsl.server.Directives.{as, complete, entity, get, path, post, _}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import nl.codestar.persistence.AppointmentActor._
import nl.codestar.persistence.{AppointmentActor, CalendarActor}
import nl.codestar.persistence.phantom.{Appointment, AppointmentsDatabase}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

trait AppointmentEndpoint extends JsonProtocol {
  implicit def system: ActorSystem
  implicit def executionContext: ExecutionContext
  implicit val timeout = Timeout(2 seconds)

  val route: Route =
    pathPrefix("appointments") {
      pathPrefix(JavaUUID) { uuid =>
        get              { getAppointment(uuid) } ~ 
        delete           { deleteAppointment(uuid) } ~
        path("reassign") { post { reassign(uuid) } } ~
        path("move")     { post { move(uuid) }  }
      } ~
      pathEnd {
        get  { getAllAppointments } ~
        post { createAppointment }
      }
    }


  private def move(uuid: UUID) = entity(as[MoveAppointment]) { move =>
    complete {
      logger.debug(s"Moving appointment $uuid to $move")
      (calendar ? move) map (_ => NoContent)
    }
  }

  private def reassign(uuid: UUID) = entity(as[ReassignAppointment]) { reassign =>
    complete {
      logger.debug(s"Reassigning appointment $uuid to $reassign")
      (calendar ? reassign) map (_ => NoContent)
    }
  }

  private def deleteAppointment(uuid: UUID) = complete {
    logger.debug(s"Deleting $uuid")
    (calendar ? AppointmentActor.CancelAppointment(uuid)) map (_ => NoContent)
  }

  private def getAppointment(uuid: UUID) = rejectEmptyResponse { // turns the result Option[T] to a 404 response
    complete {
      logger.debug(s"Get result for $uuid")
      //        AppointmentsDatabase.appointments.getById(uuid) // alternative to the find appointment
      (calendar ? GetDetails(uuid))
        .mapTo[AppointmentActor.GetDetailsResult]
        .map(_.value.map(r => Appointment(uuid, r.branchId, r.state.toString, r.advisorId, r.roomId, r.start)))
    }
  }

  private def getAllAppointments = complete(AppointmentsDatabase.appointments.getAll())

  private def createAppointment = entity(as[CreateAppointment]) { create =>
      extractUri { uri =>
        complete {
          logger.debug(s"Post new appointment at ${create.start} with ${create.advisorId} in room ${create.room}")

          val future: Future[Any] = calendar ? create
          future collect {
            case uuid: UUID =>
              val locationHeader = headers.Location(uri withPath(uri.path/uuid.toString))
              HttpResponse(Created, headers = List(locationHeader))
              
            case CommandFailed(err) => HttpResponse(BadRequest, entity = err.toString())
          }
        }
      }
    }

  def logger: LoggingAdapter

  def calendar: ActorRef
}
