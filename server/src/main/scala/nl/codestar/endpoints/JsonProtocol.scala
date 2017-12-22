package nl.codestar.endpoints

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_DATE_TIME
import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import nl.codestar.domain.domain._
import nl.codestar.persistence.AppointmentActor._
import spray.json.{
  DefaultJsonProtocol,
  DeserializationException,
  JsString,
  JsValue,
  JsonFormat
}

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Try

trait JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {

  implicit object UUIDFormat extends JsonFormat[UUID] {
    def write(uuid: UUID) = JsString(uuid.toString)

    def read(value: JsValue): UUID = value match {
      case JsString(uuid) => UUID.fromString(uuid)
      case _ =>
        throw DeserializationException("Expected hexadecimal UUID string")
    }
  }

  implicit object LocalDateTimeFormat extends JsonFormat[LocalDateTime] {
    override def write(obj: LocalDateTime): JsValue = JsString(obj.toString)

    override def read(json: JsValue): LocalDateTime = json match {
      case JsString(ldt) => LocalDateTime.parse(ldt, ISO_DATE_TIME)
      case _             => throw DeserializationException("Expected local date time")
    }
  }

  implicit object FiniteDurationFormat extends JsonFormat[FiniteDuration] {
    override def write(obj: FiniteDuration): JsValue = JsString(obj.toString)

    override def read(json: JsValue): FiniteDuration = json match {
      case JsString(ldt) =>
        Try(Duration(ldt))
          .filter(_.isFinite())
          .map(_.asInstanceOf[FiniteDuration])
          .getOrElse(
            throw DeserializationException("Expected valid finite duration"))
      case _ => throw DeserializationException("Expected valid finite duration")
    }
  }

  implicit object StateFormat extends JsonFormat[State] {
    override def write(obj: State): JsValue = obj match {
      case Busy      => JsString("Busy")
      case Tentative => JsString("Tentative")
      case Confirmed => JsString("Confirmed")
      case Cancelled => JsString("Cancelled")
      case _         => throw DeserializationException("Value is not a state")
    }

    override def read(json: JsValue): State = json match {
      case JsString("Busy")      => Busy
      case JsString("Tentative") => Tentative
      case JsString("Confirmed") => Confirmed
      case JsString("Cancelled") => Cancelled
      case _                     => throw DeserializationException("Value is not a state")
    }
  }

  implicit val appointmentFormat = jsonFormat6(
    nl.codestar.persistence.phantom.Appointment)
  implicit val appFormat = jsonFormat6(Appointment)
  implicit val aFormat = jsonFormat6(CreateAppointment)
  implicit val reassignFormat = jsonFormat2(ReassignAppointment)
  implicit val moveFormat = jsonFormat5(MoveAppointment)

}
