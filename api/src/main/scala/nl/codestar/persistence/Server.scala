package nl.codestar.persistence

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.ConfigResolveOptions.defaults
import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}
import nl.codestar.appointments.ShardedCalendar
import nl.codestar.endpoints.{AppointmentEndpoint, JsonProtocol}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

object Server extends App with JsonProtocol with AppointmentEndpoint {
//  val config =  ConfigFactory.load("cluster-application").resolve(defaults())
  System.setProperty("config.resource", "cluster-application.conf")
  // https://github.com/lightbend/config#standard-behavior
  // looks like we can use a system property to determine which file will be loaded
  val config = ConfigFactory.load().resolve(defaults())
  val host = config.getString("server.http.host")
  val port = config.getInt("server.http.port")
  val seedNodes =
    config.getList("akka.cluster.seed-nodes").asScala.mkString(",")
  val remoteHost = config.getString("akka.remote.netty.tcp.hostname")
  val remotePort = config.getString("akka.remote.netty.tcp.port")
  val journalContactpoints =
    config.getStringList("cassandra-journal.contact-points")
  val snapshotContactpoints =
    config.getStringList("cassandra-snapshot-store.contact-points")

  val l = LoggerFactory.getLogger("root")
  l.info(s"HTTP endpoint     : $host:$port")
  l.info(s"Cluster Seed nodes: $seedNodes")
  l.info(s"Remote tcp        : $remoteHost:$remotePort")
  l.info(s"Journal           : $journalContactpoints")
  l.info(s"Snapshot          : $snapshotContactpoints")

  implicit val system = ActorSystem("appointmentSystem", config)
  implicit val executionContext
    : ExecutionContextExecutor = system.dispatcher // needed for map/flatMap of future
  implicit val materializer = ActorMaterializer()
  val logger = Logging(system, getClass)

  override val calendar =
    system.actorOf(ShardedCalendar.props, ShardedCalendar.name)
  implicit override val timeout = Timeout(5.seconds)

  Http().bindAndHandle(route, host, port) map { binding =>
    logger.info(s"REST interface bound to ${binding.localAddress}")
  } recover {
    case ex =>
      logger.error(s"REST interface could not bind to $host:$port",
                   ex.getMessage)
  }
}
