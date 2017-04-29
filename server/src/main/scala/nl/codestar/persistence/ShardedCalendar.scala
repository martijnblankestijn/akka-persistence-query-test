package nl.codestar.persistence

import akka.actor.{Actor, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.persistence.PersistentActor
import nl.codestar.persistence.AppointmentActor.Command

object ShardedCalendar {
  def props = Props(new ShardedCalendar)
  def name = "sharded-calendar"
}

class ShardedCalendar extends Actor {
  ClusterSharding(context.system).start(
    AppointmentActor.shardName,               // the name of the entity type
    AppointmentActor.props(),                 // the `Props` of the entity actors that will be created by the `ShardRegion`
    ClusterShardingSettings(context.system),
    AppointmentActor.extractEntityId,         // partial function to extract the entity id and the message to send to the entity from the incoming message, if the partial function does not match the message will be `unhandled`, i.e. posted as `Unhandled` messages on the event stream
    AppointmentActor.extractShardId           // function to determine the shard id for an incoming message, only messages that passed the `extractEntityId` will be used
  )
  
  def sharedCalendar = ClusterSharding(context.system).shardRegion(AppointmentActor.shardName)
  
  override def receive: Receive = {
    case cmd: Command => sharedCalendar forward cmd
  }
}

