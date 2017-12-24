package nl.codestar.query

import java.time.LocalDateTime.ofEpochSecond
import java.time.ZoneOffset.UTC
import java.time.{LocalDateTime, YearMonth}
import java.util.UUID.fromString

import akka.persistence.query.EventEnvelope
import com.google.protobuf.timestamp.Timestamp
import nl.codestar.domain.domain.Tentative
import nl.codestar.appointments.events.{
  AppointmentCancelled,
  AppointmentCreated,
  AppointmentMoved,
  AppointmentReassigned
}
import nl.codestar.persistence.phantom.{Appointment, AppointmentsDatabase}
import nl.codestar.persistence.phantom.DateTimeConverters._
import org.slf4j.LoggerFactory

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class EventProcessor(cassandraOffsetRepository: CassandraOffsetRepository)(
    implicit ec: ExecutionContext) {
  val log = LoggerFactory.getLogger(classOf[EventProcessor])

  def handle(e: EventEnvelope): Future[String] = {
    if (log.isDebugEnabled())
      log.debug(s"Event id={}, seq={}, offset={}: {}",
                e.persistenceId,
                e.sequenceNr.toString,
                e.offset.toString,
                e.event.toString.replace('\n', ' '))

    val result: Future[String] = e.event match {
      case a: AppointmentCancelled =>
        handleAppointmentCancelled(e.persistenceId, a)
      case a: AppointmentCreated => handleAppointmentCreated(e.persistenceId, a)
      case app: AppointmentMoved => handleAppointmentMoved(e.persistenceId, app)
      case AppointmentReassigned(advisorId) =>
        log.trace("Reassign to {}", advisorId)
        Future.successful(e.persistenceId)
    }

    result.onComplete {
      case Success(_) => cassandraOffsetRepository.saveOffset(e.offset)
      case Failure(t) => log.error(t.getMessage, t)
    }
    result
  }

  private def handleAppointmentCancelled(
      persistenceId: String,
      evt: AppointmentCancelled): Future[String] = {
    def removeFromDatabase(apt: Appointment) =
      for {
        _ <- AppointmentsDatabase.appointments.remove(apt.id)
        _ <- AppointmentsDatabase.appointmentsByBranchId.remove(
          apt.branchId,
          YearMonth.from(apt.start),
          apt.id)
      } yield apt.id

    log.trace("Remove {} from Query table as it is Cancelled", persistenceId)
    for {
      optionAppointment <- AppointmentsDatabase.appointments.getById(
        fromString(persistenceId))
      appt <- optionAppointment
        .map(removeFromDatabase)
        .getOrElse(successful(persistenceId))
    } yield persistenceId
  }

  private def handleAppointmentCreated(
      persistenceId: String,
      a: AppointmentCreated): Future[String] = {
    log.trace("Creating new appointment for branch {}", a.branchId)

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

  def handleAppointmentMoved(persistenceId: String,
                             app: AppointmentMoved): Future[String] = {
    def createCopy(appointment: Appointment) =
      appointment.copy(
        advisorId = fromString(app.advisorId),
        roomId = Option(fromString(app.roomId)),
        start = app.startDateTime
          .map(ts => timestampToLocalDateTime(ts))
          .getOrElse(LocalDateTime.now),
        branchId = fromString(app.branchId)
      )

    def updateByBranchId(appointment: Appointment): Future[String] =
      if (appointment.branchId == fromString(app.branchId))
        AppointmentsDatabase.appointmentsByBranchId
          .update(appointment)
          .map(_ => persistenceId)
      else Future.failed(new UnsupportedOperationException("NOT SUPPORTED YET"))

    // should be moved
    implicit def timestampToLocalDateTime(timestamp: Timestamp): LocalDateTime =
      ofEpochSecond(timestamp seconds, timestamp nanos, UTC)
    log.trace("Move an appointment to {} at {}",
              app.advisorId,
              app.startDateTime,
              "")

    AppointmentsDatabase.appointments
      .getById(fromString(persistenceId))
      .flatMap(
        maybeAppointment =>
          maybeAppointment
            .map { appointment =>
              val copy = createCopy(appointment)
              for {
                _ <- AppointmentsDatabase.appointments.update(copy)
                _ <- updateByBranchId(copy)
              } yield persistenceId
            }
            .getOrElse(Future.failed(new IllegalStateException(
              s"No appointment found for $persistenceId")))
      )
  }
}
