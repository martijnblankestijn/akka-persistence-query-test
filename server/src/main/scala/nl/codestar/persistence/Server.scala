package nl.codestar.persistence

import akka.actor.{ActorSystem, Props}
import akka.cluster.ddata.Replicator.Get
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}
import nl.codestar.endpoints.{AppointmentEndpoint, JsonProtocol}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._


object Server extends App with JsonProtocol with AppointmentEndpoint {
  val config = 
      ConfigFactory.load("cluster-application").resolve(ConfigResolveOptions.defaults())
      //ConfigFactory.defaultApplication().resolve()
  implicit val system = ActorSystem("appointmentSystem", config)
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher // needed for map/flatMap of future
  implicit val materializer = ActorMaterializer()
  val logger = Logging(system, getClass)

//  override val calendar = system.actorOf(CalendarActor.props(), "appointments")
  override val calendar = system.actorOf(ShardedCalendar.props, ShardedCalendar.name)
  implicit override val timeout = Timeout(2.seconds)

  val host = config.getString("server.http.host")
  val port = config.getInt("server.http.port")

  Http().bindAndHandle(route, host, port) map { binding =>
    logger.info(s"REST interface bound to ${binding.localAddress}")
  } recover { case ex =>
    logger.error(s"REST interface could not bind to $host:$port", ex.getMessage)
  }
}
