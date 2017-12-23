package nl.codestar.appointments

import java.time.LocalDateTime
import java.time.LocalDateTime._

import cats.data.Validated.{invalidNel, valid}
import cats.data.ValidatedNel

import scala.collection.immutable.Seq
import scala.concurrent.duration.{FiniteDuration, MINUTES}

object Validations {
  type Validation[T] = ValidatedNel[Error, T]
  val minimumAppointmentDuration = FiniteDuration(0, MINUTES)
  val maximumAppointmentDuration = FiniteDuration(240, MINUTES)

  def validateInTheFuture(dateTime: LocalDateTime): Validation[LocalDateTime] =
    if (dateTime isAfter now) valid(dateTime)
    else
      invalidNel(
        ValidationError("NotInTheFuture",
                        "Date time should be in the future",
                        Seq(dateTime)))

  private def validateMinimumDuration(
      duration: FiniteDuration): Validation[FiniteDuration] =
    if (duration >= minimumAppointmentDuration) valid(duration)
    else
      invalidNel(
        ValidationError("DurationTooShort",
                        "Duration is too short",
                        Seq(duration)))

  private def validateMaximumDuration(
      duration: FiniteDuration): Validation[FiniteDuration] =
    if (duration <= maximumAppointmentDuration) valid(duration)
    else
      invalidNel(
        ValidationError("DurationTooLong",
                        "Duration is too long",
                        Seq(duration)))

  // getrapte validatie
  def validateDuration(duration: FiniteDuration): Validation[FiniteDuration] =
    validateMinimumDuration(duration) andThen validateMaximumDuration
}
