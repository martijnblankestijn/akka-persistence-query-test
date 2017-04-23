package nl.codestar.query

import java.time.{LocalDateTime, YearMonth}
import java.util.UUID.fromString

import akka.actor.{Actor, ActorLogging, Props}
import akka.persistence.query._
import com.outworkers.phantom.dsl.ResultSet
import nl.codestar.persistence.AppointmentActor.Tentative
import nl.codestar.persistence.events.{AppointmentCancelled, AppointmentCreated, AppointmentMoved, AppointmentReassigned, timestampToLocalDateTime}
import nl.codestar.query.phantom.DateTimeConverters.timestamp2LocalDateTime
import nl.codestar.query.phantom.{Appointment, AppointmentsDatabase}

import scala.concurrent.Future
import scala.concurrent.Future.successful
import scala.util.{Failure, Success}


class EventProcessor(cassandraOffsetRepository: CassandraOffsetRepository) extends Actor with ActorLogging {
  implicit val ec = context.dispatcher

  override def receive: Receive = {
    case EventEnvelope(offset, persistenceId, sequenceNr, event) =>
      log.debug(s"Got event for ($persistenceId, $sequenceNr) with offset $offset: " + event.toString.replace('\n', ' ' ))

      val result: Future[Any] = event match {
        case a: AppointmentCancelled => handleAppointmentCancelled(persistenceId, a)
        case a: AppointmentCreated => handleAppointmentCreated(persistenceId, a)
        case app: AppointmentMoved => handleAppointmentMoved(persistenceId, app)
        case AppointmentReassigned(advisorId) =>
          log.debug(s"Reassign to $advisorId")
          Future.successful(persistenceId)
      }

      result.onComplete {
        case Success(_) => cassandraOffsetRepository.saveOffset(offset)
        case Failure(t) => t.printStackTrace
      }
  }

  private def handleAppointmentCancelled(persistenceId: String, evt: AppointmentCancelled): Future[String] = {
    def removeFromDatabase(apt: Appointment) = for {
      _ <- AppointmentsDatabase.appointments.remove(apt.id)
      _ <- AppointmentsDatabase.appointmentsByBranchId.remove(apt.branchId, YearMonth.from(apt.start), apt.id)
    } yield apt.id

    log.debug(s"Remove $persistenceId from Query table as it is Cancelled")
    for {
      optionAppointment <- AppointmentsDatabase.appointments.getById(fromString(persistenceId))
      appt <- optionAppointment.map(removeFromDatabase).getOrElse(successful(persistenceId))
    } yield persistenceId
  }


  private def handleAppointmentCreated(persistenceId: String, a: AppointmentCreated): Future[String] = {
    log.debug(s"Creating new appointment for branch ${a.branchId}")

    val appointment = Appointment(
      id = fromString(persistenceId),
      branchId = fromString(a.branchId),
      state = Tentative.toString,
      advisorId = fromString(a.advisorId),
      roomId = if (a.roomId == "") None else Some(fromString(a.roomId)),
      start = a.start.map(timestamp2LocalDateTime).getOrElse(LocalDateTime.now)
    )
    for {
      _ <- AppointmentsDatabase.appointments.store(appointment)
      _ <- AppointmentsDatabase.appointmentsByBranchId.store(appointment)
    } yield persistenceId
  }

  def handleAppointmentMoved(persistenceId: String, app: AppointmentMoved) = {
    log.debug(s"Move an appointment to ${app.advisorId} at ${app.startDateTime}")

    def updateByBranchId(appointment: Appointment): Future[ResultSet] = {
      if (appointment.branchId == fromString(app.branchId))
        AppointmentsDatabase.appointmentsByBranchId.update(appointment)
      else {
        throw new UnsupportedOperationException("NOT SUPPORTED YET")
      }
    }

    AppointmentsDatabase.appointments.getById(fromString(persistenceId))
      .map(_.map { (appointment: Appointment) =>
        val s = appointment.copy(
          advisorId = fromString(app.advisorId),
          roomId = Option(fromString(app.roomId)),
          start = app.startDateTime.map(ts => timestampToLocalDateTime(ts)).getOrElse(LocalDateTime.now),
          branchId = fromString(app.branchId)
        )
        AppointmentsDatabase.appointments.update(appointment)
          .flatMap(_ => updateByBranchId(appointment))
      })
  }
}

object EventProcessor {
  def props(cassandraOffsetRepository: CassandraOffsetRepository) = Props(new EventProcessor(cassandraOffsetRepository))
}