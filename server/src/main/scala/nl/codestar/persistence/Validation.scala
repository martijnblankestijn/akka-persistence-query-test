package nl.codestar.persistence

import java.time.LocalDateTime
import java.time.LocalDateTime.now

import cats.data.Validated._
import cats.data.ValidatedNel

import scala.collection.immutable.Seq
import scala.concurrent.duration._

object Validations {
  type Validation[T] = ValidatedNel[Error, T]
  val minimumAppointmentDuration = FiniteDuration(0, MINUTES)
  val maximumAppointmentDuration = FiniteDuration(240, MINUTES)
  
  def validateInTheFuture(dateTime: LocalDateTime): Validation[LocalDateTime] =
    if (dateTime isAfter now) valid(dateTime)
    else invalidNel(ValidationError("NotInTheFuture", "Date time should be in the future", Seq(dateTime)))


  private def validateMinimumDuration(duration: FiniteDuration): Validation[FiniteDuration] =
    if(duration >= minimumAppointmentDuration) valid(duration)
    else invalidNel(ValidationError("DurationTooShort", "Duration is too short", Seq(duration)))

  private def validateMaximumDuration(duration: FiniteDuration): Validation[FiniteDuration] =
    if(duration <= maximumAppointmentDuration) valid(duration)
    else invalidNel(ValidationError("DurationTooLong", "Duration is too long", Seq(duration)))

  // getrapte validatie
  def validateDuration(duration: FiniteDuration): Validation[FiniteDuration] = 
    validateMinimumDuration(duration) andThen validateMaximumDuration   
}

sealed trait Error {
  def code: String
  def msg: String
  def values: Seq[AnyRef]
}

case class ValidationError(code: String, msg: String, values: Seq[AnyRef]) extends Error
case class AlreadyExists(msg: String, values: Seq[AnyRef]) extends Error {
  val code = "Exists"
}
case class CancelledError(msg: String, values: Seq[AnyRef]) extends Error {
  val code  = "Cancelled"
}