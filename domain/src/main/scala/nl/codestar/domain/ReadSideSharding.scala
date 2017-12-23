package nl.codestar.domain

import java.lang.Math.abs

import scala.collection.immutable

object AppointmentReadSide {

  /** Base name of a shard. */
  private val readShardName = "appointment"

  /** The number of shards to use for read side sharding. Determines */
  private val readNumberOfShards = 20

  val createReadSideShardId: String => String = ReadSideSharding
    .readSideFromPersistenceId(readShardName, readNumberOfShards)
  val readShards: immutable.Seq[String] =
    ReadSideSharding.readSideTags(readShardName, readNumberOfShards)
}

object ReadSideSharding {

  /**
    * @param baseTag the base tag
    * @param numberOfShards number of read-side shards
    * @return the function to create the shard name based upon the persistence id
    */
  def readSideFromPersistenceId(baseTag: String,
                                numberOfShards: Int): String => String =
    persistenceId =>
      createShardName(baseTag, abs(persistenceId.hashCode % numberOfShards))

  /**
    *
    * @param baseTag the base tag
    * @param numberOfShards number of read-side shards
    * @return sequence of the possible read-side shard names.
    */
  def readSideTags(baseTag: String,
                   numberOfShards: Int): immutable.Seq[String] = {
    require(numberOfShards > 0)
    (0 to numberOfShards).map(i => createShardName(baseTag, i))
  }

  private def createShardName(baseTag: String, shardId: Int) = baseTag + shardId
}
