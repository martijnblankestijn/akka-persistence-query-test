package nl.codestar.persistence

import akka.persistence.journal.{Tagged, WriteEventAdapter}
import nl.codestar.persistence.events.AppointmentEvent
import org.slf4j.LoggerFactory

class DomainObjectEventAdapter extends WriteEventAdapter {
  private val logger = LoggerFactory.getLogger(classOf[DomainObjectEventAdapter])
  private val tags = Set("appointment")

  override def manifest(event: Any): String = ""
  override def toJournal(event: Any): Any = {
    logger.debug(s"Tagging event with $tags ${eventToString(event)}")
    event match {
      case _: AppointmentEvent => Tagged(event, tags)
      case _ => event
    }
  }

  private def eventToString(event: Any): String = {
    event.toString.replace('\n', ' ')
  }
}
