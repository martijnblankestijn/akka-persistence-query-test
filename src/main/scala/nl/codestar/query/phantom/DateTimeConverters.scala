package nl.codestar.query.phantom

import java.time.Instant.ofEpochSecond
import java.time.ZoneId.systemDefault
import java.time.{LocalDateTime, ZoneId, ZoneOffset}

import com.google.protobuf.timestamp.Timestamp
import org.joda.time.DateTime

object DateTimeConverters {
  def localDateTime2DateTime(dateTime: LocalDateTime): DateTime = new DateTime(dateTime.atZone(systemDefault()).toInstant.toEpochMilli)

  def dateTime2LocalDateTime(dateTime: DateTime): LocalDateTime = LocalDateTime.of(
    dateTime.getYear, dateTime.getMonthOfYear, dateTime.getDayOfMonth,
    dateTime.getHourOfDay, dateTime.getMinuteOfHour, dateTime.getSecondOfMinute)


  def fromLocalDateTime(localDateTime: java.time.LocalDateTime): Timestamp =  {
    val instant = localDateTime.toInstant(ZoneOffset.UTC)
    Timestamp(instant.getEpochSecond, instant.getNano)
  }

  def timestamp2LocalDateTime(timestamp: Timestamp): LocalDateTime = 
    LocalDateTime.ofInstant(ofEpochSecond(timestamp.seconds, timestamp.nanos), ZoneId.of("UTC"))
}
