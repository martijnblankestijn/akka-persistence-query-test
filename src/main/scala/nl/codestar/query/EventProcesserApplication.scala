package nl.codestar.query

import akka.actor.ActorSystem
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal.Identifier
import akka.persistence.query._
import akka.stream.ActorMaterializer
import com.datastax.driver.core._
import com.typesafe.config.Config
import nl.codestar.query.CassandraConfiguration.createCluster
import nl.codestar.query.CassandraOffsetRepository.prepareInsert
import nl.codestar.query.JournalProcessor._

import collection.JavaConverters._

class JournalProcessor(cassandraOffsetRepository: CassandraOffsetRepository)(implicit system: ActorSystem) {
  implicit private val executionContext = system.dispatcher
  implicit private val mat = ActorMaterializer() // from akka.streams: "an ActorMaterializer will execute every step of a transformation pipeline" 
  val eventProcessor = system.actorOf(EventProcessor.props(cassandraOffsetRepository))

  def processAll() =
    PersistenceQuery(system)
      .readJournalFor[CassandraReadJournal](Identifier)
      .eventsByTag(eventTag, cassandraOffsetRepository.loadedOffset)
      .runForeach { (event: EventEnvelope) => eventProcessor ! event }
}

object JournalProcessor {
  val eventTag = "appointment"
}

object EventProcesserApplication extends App {
  implicit private val system = ActorSystem("event-processor-appointments")
  implicit val executionContext = system.dispatcher

  val session = createCluster(system.settings.config).newSession()

  CassandraOffsetRepository(session, prepareInsert(session), "appointment-processor", "appointment")
    .map(repo =>
      new JournalProcessor(repo).processAll())

}


object CassandraConfiguration {
  def createCluster(config: Config): Cluster = {
    val contactpoints = config.getStringList("cassandra-journal.contact-points").asScala
    Cluster.builder().addContactPoints(contactpoints: _*).build()
  }
}





