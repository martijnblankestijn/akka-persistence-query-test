package nl.codestar.query

import akka.actor.ActorSystem
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal.Identifier
import akka.persistence.query._
import akka.stream._
import akka.stream.scaladsl.Sink
import com.datastax.driver.core._
import com.typesafe.config.Config
import nl.codestar.persistence.phantom.AppointmentReadSide
import nl.codestar.query.CassandraConfiguration._
import nl.codestar.query.CassandraOffsetRepository.prepareInsert
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.duration._

class JournalProcessor(shardName: String, cassandraOffsetRepository: CassandraOffsetRepository)(implicit system: ActorSystem) {
  implicit private val executionContext = system.dispatcher
  implicit private val mat = ActorMaterializer() // from akka.streams: "an ActorMaterializer will execute every step of a transformation pipeline"
  val eventProcessor = new EventProcessor(cassandraOffsetRepository)

  def processAll() =  eventByTag.map(eventProcessor.handle).to(Sink.ignore).run()

//  def processAll2() = {
//    val graph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
//      import GraphDSL.Implicits._
//      val numberOfPorts = 2
//      // why the partition?
//      // Line of reasoning is as follows:
//      // We do not want one element to be processed, one after each other.
//      // But if we use the async query capabilities of Cassandra, we run the risk that updates for one persistent entity
//      // will happen out-of-order.
//      // Hopefully this is the nice middleground. We'll see ....
//      val partition = builder.add(Partition(numberOfPorts, (e: EventEnvelope) => e.persistenceId.hashCode % numberOfPorts))
//      val merge     = builder.add(Merge[Future[String]](numberOfPorts))
//      val processEvent = Flow[EventEnvelope].map(eventProcessor.handle)
//
//      eventByTag ~> partition ~> processEvent ~> merge ~> Sink.ignore
//      (1 until numberOfPorts).foreach(_ => partition ~> processEvent ~> merge) 
//      ClosedShape
//    })
//    graph.run()
//  }

  private def eventByTag = {
    PersistenceQuery(system)                                             // Akka Extension for Persistence
      .readJournalFor[CassandraReadJournal](Identifier)                  // Get the right journal                      
      .eventsByTag(shardName, cassandraOffsetRepository.loadedOffset)    // retrieving events that were marked with a given tag
  }
}

object EventProcesserApplication extends App {
  val logger = LoggerFactory.getLogger("root")

  implicit val system = ActorSystem("event-processor-appointments")
  implicit val executionContext = system.dispatcher


  val tableName = "appointmentquery.offsetStore"
  val (cluster, insert) = retry(12, 5 seconds){
    val cluster = createCluster(system.settings.config)
    (cluster, prepareInsert(cluster.newSession(), tableName))
  }
  logger.info("Starting processing events from the journal")
  AppointmentReadSide.readShards.map{ shardName =>
    CassandraOffsetRepository(cluster.newSession(), insert, s"processor-$shardName", shardName)
        .map { repo =>
          logger.info("Starting processing events for shard {} from offset {}", shardName, repo.loadedOffset: Any)
          new JournalProcessor(shardName, repo).processAll()
        }
  }
  
  @annotation.tailrec
  def retry[T](n: Int, waitTime: FiniteDuration)(fn: => T): T = {
    val r = try { Some(fn) } catch { 
      case e: Exception if n > 1 => None
      case e: Exception =>
        logger.error("Stopping now .....", e)
        System.exit(1)
        None
    }
    r match {
      case Some(x) => x
      case None =>
        logger.warn("Attempt {}, waiting for {}.", n, waitTime)
        Thread.sleep(waitTime.toMillis)
        retry(n-1, waitTime)(fn)
    }
  }
}


object CassandraConfiguration {
  def createCluster(config: Config): Cluster = {
    val contactpoints = config.getStringList("cassandra-journal.contact-points").asScala
    Cluster.builder().addContactPoints(contactpoints: _*).build()
  }
}
