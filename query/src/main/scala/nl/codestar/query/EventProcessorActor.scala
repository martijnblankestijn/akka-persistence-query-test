package nl.codestar.query

import akka.actor.{Actor, ActorLogging, Props}
import akka.persistence.query._


class EventProcessorActor(cassandraOffsetRepository: CassandraOffsetRepository) extends Actor with ActorLogging {
  implicit val ec = context.dispatcher
  val eventProcessor = new EventProcessor(cassandraOffsetRepository)

  override def receive: Receive = {
    case e: EventEnvelope => eventProcessor.handle(e)
  }
}

object EventProcessorActor {
  def props(cassandraOffsetRepository: CassandraOffsetRepository) = Props(new EventProcessorActor(cassandraOffsetRepository))
}