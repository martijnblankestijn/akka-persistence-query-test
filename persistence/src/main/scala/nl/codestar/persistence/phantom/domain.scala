package nl.codestar.persistence.phantom

import java.time.format.DateTimeFormatter
import java.time.{LocalDate => _, _}
import java.util.UUID

import com.datastax.driver.core.ConsistencyLevel.LOCAL_QUORUM
import com.outworkers.phantom.CassandraTable
import com.outworkers.phantom.dsl._
import nl.codestar.persistence.phantom.DateTimeConverters.{dateTime2LocalDateTime, localDateTime2DateTime}

import scala.concurrent.Future

case class Appointment(id: UUID, branchId: UUID, state: String, advisorId: UUID, roomId: Option[UUID], start: LocalDateTime)

class AppointmentTable extends CassandraTable[AppointmentRepository, Appointment] {
  override val tableName = "appointment"

  override def fromRow(row: Row): Appointment =
    Appointment(
      id(row),
      branchId(row),
      state(row),
      advisorId(row),
      roomId(row),
      dateTime2LocalDateTime(start(row))
    )

  object id extends UUIDColumn(this) with PartitionKey

  object branchId extends UUIDColumn(this)

  object state extends StringColumn(this)

  object advisorId extends UUIDColumn(this)

  object roomId extends OptionalUUIDColumn(this)

  object start extends DateTimeColumn(this)

}

abstract class AppointmentRepository extends AppointmentTable with RootConnector {
  def store(appointment: Appointment): Future[ResultSet] = {
    insert()
      .value(_.id, appointment.id)
      .value(_.branchId, appointment.branchId)
      .value(_.state, appointment.state)
      .value(_.advisorId, appointment.advisorId)
      .value(_.roomId, appointment.roomId)
      .value(_.start, localDateTime2DateTime(appointment.start))
      .consistencyLevel_=(LOCAL_QUORUM)
      .future()
  }

  def getAll(): Future[Seq[Appointment]] = select.all().fetch()
  
  def getById(id: UUID): Future[Option[Appointment]] = select.where(_.id eqs id).one()

  def remove(id: UUID): Future[ResultSet] = delete.where(_.id eqs id).future()

  def update(appointment: Appointment): Future[ResultSet] = {
    update()
      .where(_.id eqs appointment.id)
      .modify(_.branchId setTo appointment.branchId)
      .and(_.start setTo localDateTime2DateTime(appointment.start))
      .and(_.roomId setTo appointment.roomId)
      .and(_.advisorId setTo appointment.advisorId)
      .future()
  }
}

class AppointmentByBranchIdTable extends CassandraTable[AppointmentByBranchIdRepository, Appointment] {
  override val tableName = "appointment_by_branchid"

  override def fromRow(row: Row): Appointment = Appointment(
    appointmentId(row),
    branchId(row),
    state(row),
    advisorId(row),
    roomId(row),
    dateTime2LocalDateTime(start(row))
  )

  object branchId extends UUIDColumn(this) with PartitionKey

  object yearmonth extends StringColumn(this) with PartitionKey

  object appointmentId extends UUIDColumn(this) with ClusteringOrder

  object state extends StringColumn(this)

  object advisorId extends UUIDColumn(this)

  object roomId extends OptionalUUIDColumn(this)

  object start extends DateTimeColumn(this)

}

abstract class AppointmentByBranchIdRepository extends AppointmentByBranchIdTable with RootConnector {
  def dateTimeToYearMonthString(dateTime: LocalDateTime): String = DateTimeFormatter.ofPattern("YYYYMM").format(dateTime)
  
  def store(appointment: Appointment): Future[ResultSet] = {
    insert()
      .value(_.appointmentId, appointment.id)
      .value(_.branchId, appointment.branchId)
      .value(_.state, appointment.state)
      .value(_.advisorId, appointment.advisorId)
      .value(_.roomId, appointment.roomId)
      .value(_.start, localDateTime2DateTime(appointment.start))
      .value(_.yearmonth, dateTimeToYearMonthString(appointment.start))
      .consistencyLevel_=(LOCAL_QUORUM)
      .future()
  }

  def remove(branchId: UUID, yearMonth: YearMonth, appointmentId: UUID): Future[ResultSet] =
    delete.where(_.branchId eqs branchId).and(_.yearmonth eqs yearMonth.toString).future()

  def update(appointment: Appointment): Future[ResultSet] = {
    update()
      .where(_.appointmentId eqs appointment.id)
        .and(_.yearmonth eqs dateTimeToYearMonthString(appointment.start))
        .and(_.branchId eqs appointment.branchId)  
      .modify(_.start setTo localDateTime2DateTime(appointment.start))
      .and(_.roomId setTo appointment.roomId)
      .and(_.advisorId setTo appointment.advisorId)
      .future()
  }

}