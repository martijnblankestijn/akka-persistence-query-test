/*
package nl.codestar.persistence

// NOT USED ANYMORE
import akka.persistence.journal.{Tagged, WriteEventAdapter}
import nl.codestar.appointments.events.AppointmentEvent
import org.slf4j.LoggerFactory.getLogger

class DomainObjectEventAdapter extends WriteEventAdapter {
  private val logger =  getLogger(classOf[DomainObjectEventAdapter])
  private val tags = Set("appointment")

  override def manifest(event: Any): String = ""
  override def toJournal(event: Any): Any = {
    if (logger.isDebugEnabled())
      logger.debug("Tagging event {} with {}", eventToString(event), tags, "")

    event match {
      case _: AppointmentEvent => Tagged(event, tags)
      case _                   => event
    }
  }

  private def eventToString(event: Any): String =
    event.toString.replace('\n', ' ')
}
 */
