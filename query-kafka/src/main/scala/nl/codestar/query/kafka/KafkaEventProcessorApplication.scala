package nl.codestar.query.kafka

import akka.actor.ActorSystem
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal.Identifier
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import nl.codestar.query.{CassandraOffsetRepository, EventProcessor}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

object KafkaEventProcessorApplication {
  val logger = LoggerFactory.getLogger("root")

  implicit val system = ActorSystem("event-processor-kafka")
  implicit val executionContext = system.dispatcher

}

class KafkaJournalProcessor (shardName: String, cassandraOffsetRepository: CassandraOffsetRepository)(implicit system: ActorSystem) {
    implicit private val executionContext = system.dispatcher
    implicit private val mat = ActorMaterializer() // from akka.streams: "an ActorMaterializer will execute every step of a transformation pipeline"
    val eventProcessor = new KafkaEventProcessor(cassandraOffsetRepository)

    def processAll() =  eventByTag.map(eventProcessor.handle).to(Sink.ignore).run()

    private def eventByTag = {
      PersistenceQuery(system)                                             // Akka Extension for Persistence
        .readJournalFor[CassandraReadJournal](Identifier)                  // Get the right journal                      
        .eventsByTag(shardName, cassandraOffsetRepository.loadedOffset)    // retrieving events that were marked with a given tag
    }
  
}

class KafkaEventProcessor(cassandraOffsetRepository: CassandraOffsetRepository)(implicit ec: ExecutionContext) {
  def handle(e: EventEnvelope): Future[String] = {
    ???
  }
}