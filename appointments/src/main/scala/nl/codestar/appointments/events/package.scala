package nl.codestar.persistence

import java.time.LocalDateTime
import java.time.LocalDateTime.ofEpochSecond
import java.time.ZoneOffset.UTC
import java.util.UUID
import java.util.UUID.fromString
import java.util.concurrent.TimeUnit.SECONDS

import com.google.protobuf.duration.Duration
import com.google.protobuf.timestamp.Timestamp

import scala.concurrent.duration.FiniteDuration

object ProtobufConversions {
  implicit def timestampToLocalDateTime(timestamp: Timestamp): LocalDateTime =
    ofEpochSecond(timestamp seconds, timestamp nanos, UTC)
  implicit def localDateTimetoTimestamp(time: LocalDateTime): Timestamp =
    Timestamp(time toEpochSecond UTC, time getNano)
  implicit def uuidToString(uuid: UUID): String = uuid toString
  implicit def stringToUuid(uuid: String): UUID = fromString(uuid)
  implicit def optionStringToUuid(option: Option[String]): Option[UUID] =
    option map fromString
  implicit def optionUuidToString(option: Option[UUID]): String =
    option.map(_.toString).getOrElse("")
  implicit def optionTimestampToLocalDateTime(
      option: Option[Timestamp]): LocalDateTime =
    option
      .map(timestampToLocalDateTime)
      .getOrElse(
        throw new IllegalStateException("Timestamp should never be null"))
  implicit def optionDurationToFiniteDuration(
      option: Option[Duration]): FiniteDuration =
    option
      .map(durationToFiniteToDuration)
      .getOrElse(
        throw new IllegalMonitorStateException("Duration should never be null"))

  implicit def finiteToDuration(duration: FiniteDuration): Duration = {
    /// is this ok??? TOODO can the nanos be zero>??
    Duration(duration.toSeconds, 0)
  }

  implicit def durationToFiniteToDuration(
      duration: Duration): FiniteDuration = {
    /// is this ok??? Only use the seconds???
    FiniteDuration(duration.seconds, SECONDS)
  }
  implicit def stringToOptionalUuid(value: String): Option[UUID] =
    if (value == "") None else Some(fromString(value))

}
