package nl.codestar.persistence.phantom

import java.time.Instant.ofEpochSecond
import java.time.{LocalDateTime, ZoneId}

import com.google.protobuf.timestamp.Timestamp

object DateTimeConverters {
  def timestamp2LocalDateTime(timestamp: Timestamp): LocalDateTime =
    LocalDateTime.ofInstant(ofEpochSecond(timestamp.seconds, timestamp.nanos),
                            ZoneId.of("UTC"))
}
