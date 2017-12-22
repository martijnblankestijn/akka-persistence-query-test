package nl.codestar.persistence.phantom

import java.time.{LocalDateTime, YearMonth}
import java.time.format.DateTimeFormatter
import java.util.UUID

import com.datastax.driver.core.ConsistencyLevel.LOCAL_QUORUM
import com.outworkers.phantom.dsl._
import com.outworkers.phantom.jdk8._

import scala.concurrent.Future

case class Appointment(id: UUID,
                       branchId: UUID,
                       state: String,
                       advisorId: UUID,
                       roomId: Option[UUID],
                       start: LocalDateTime)

abstract class AppointmentTable
    extends Table[AppointmentRepository, Appointment] {
  override val tableName = "appointment"

  override def fromRow(row: Row): Appointment =
    Appointment(
      id(row),
      branchId(row),
      state(row),
      advisorId(row),
      roomId(row),
      start(row)
    )

  object id extends UUIDColumn with PartitionKey

  object branchId extends Col[UUID]

  object state extends Col[String]

  object advisorId extends Col[UUID]

  object roomId extends Col[Option[UUID]]

  object start extends Col[LocalDateTime]

}

abstract class AppointmentRepository
    extends AppointmentTable
    with RootConnector {
  
  def store(appointment: Appointment): Future[ResultSet] = {
    insert()
      .value(_.id, appointment.id)
      .value(_.branchId, appointment.branchId)
      .value(_.state, appointment.state)
      .value(_.advisorId, appointment.advisorId)
      .value(_.roomId, appointment.roomId)
      .value(_.start, appointment.start)
      .consistencyLevel_=(LOCAL_QUORUM)
      .future()
  }

  def getAll(): Future[Seq[Appointment]] = select.all().fetch()

  def getById(id: UUID): Future[Option[Appointment]] =
    select.where(_.id eqs id).one()

  def remove(id: UUID): Future[ResultSet] = delete.where(_.id eqs id).future()

  def update(appointment: Appointment): Future[ResultSet] = {
    update()
      .where(_.id eqs appointment.id)
      .modify(_.branchId setTo appointment.branchId)
      .and(_.start setTo appointment.start)
      .and(_.roomId setTo appointment.roomId)
      .and(_.advisorId setTo appointment.advisorId)
      .future()
  }
}

abstract class AppointmentByBranchIdTable
    extends Table[AppointmentByBranchIdRepository, Appointment] {
  
  override val tableName = "appointment_by_branchid"

  override def fromRow(row: Row): Appointment = Appointment(
    appointmentId(row),
    branchId(row),
    state(row),
    advisorId(row),
    roomId(row),
    start(row)
  )

  object branchId extends UUIDColumn with PartitionKey

  object yearmonth extends StringColumn with PartitionKey

  object appointmentId extends UUIDColumn with ClusteringOrder

  object state extends Col[String]

  object advisorId extends Col[UUID]

  object roomId extends Col[Option[UUID]]

  object start extends Col[LocalDateTime]

}

abstract class AppointmentByBranchIdRepository
    extends AppointmentByBranchIdTable
    with RootConnector {
  def dateTimeToYearMonthString(dateTime: LocalDateTime): String =
    DateTimeFormatter.ofPattern("YYYYMM").format(dateTime)

  def store(appointment: Appointment): Future[ResultSet] = {
    insert()
      .value(_.appointmentId, appointment.id)
      .value(_.branchId, appointment.branchId)
      .value(_.state, appointment.state)
      .value(_.advisorId, appointment.advisorId)
      .value(_.roomId, appointment.roomId)
      .value(_.start, appointment.start)
      .value(_.yearmonth, dateTimeToYearMonthString(appointment.start))
      .consistencyLevel_=(LOCAL_QUORUM)
      .future()
  }

  def remove(branchId: UUID,
             yearMonth: YearMonth,
             appointmentId: UUID): Future[ResultSet] =
    delete
      .where(_.branchId eqs branchId)
      .and(_.yearmonth eqs yearMonth.toString)
      .future()

  def update(appointment: Appointment): Future[ResultSet] = {
    update()
      .where(_.appointmentId eqs appointment.id)
      .and(_.yearmonth eqs dateTimeToYearMonthString(appointment.start))
      .and(_.branchId eqs appointment.branchId)
      .modify(_.start setTo appointment.start)
      .and(_.roomId setTo appointment.roomId)
      .and(_.advisorId setTo appointment.advisorId)
      .future()
  }

}
