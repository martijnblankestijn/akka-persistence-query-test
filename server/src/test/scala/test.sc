import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit.{HOURS, MINUTES}
import java.time.{LocalDateTime, OffsetDateTime}

import akka.http.scaladsl.model.Uri
import nl.codestar.persistence.Command

val uri: Uri= "http://localhost:8080/appointments/f29fd030-6eb1-4d96-9d43-b1e854227638"
uri.copy(path = uri.path / "reassign")

val tomorrow = LocalDateTime.now.plusDays(1)
tomorrow.truncatedTo(MINUTES).toString

OffsetDateTime.now(UTC).truncatedTo(HOURS)

classOf[Command]