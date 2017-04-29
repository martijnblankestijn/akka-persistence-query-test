package nl.codestar.query

import akka.Done
import akka.persistence.query._
import com.datastax.driver.core.{ResultSetFuture, _}
import com.google.common.util.concurrent.{FutureCallback, Futures}
import com.outworkers.phantom.dsl.ResultSet
import nl.codestar.query.CassandraConversions._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

// original from Lagom com.lightbend.lagom.internal.persistence.cassandra.CassandraOffsetDao
class CassandraOffsetRepository private(session: Session, statement: PreparedStatement,
                                        eventProcessorId: String, tag: String, val loadedOffset: Offset) {

  def saveOffset(offset: Offset)(implicit ec: ExecutionContext): Future[Done] =
    session.executeAsync(bindSaveOffset(offset)).map(_ => Done)

  def bindSaveOffset(offset: Offset): BoundStatement = {
    offset match {
      case NoOffset => statement.bind(eventProcessorId, tag, null, null)
      case seq: Sequence => statement.bind(eventProcessorId, tag, null, java.lang.Long.valueOf(seq.value))
      case uuid: TimeBasedUUID => statement.bind(eventProcessorId, tag, uuid.value, null)
    }
  }
}

object CassandraOffsetRepository {
  def prepareInsert(session: Session, tableName: String): PreparedStatement = 
    session.prepare("INSERT INTO appointmentquery.offsetStore (eventProcessorId, tag, timeUuidOffset, sequenceOffset) VALUES (?, ?, ?, ?)")
  
  def apply(session: Session, insertStatement: PreparedStatement, eventProcessorId: String, tag: String)
           (implicit executionContext: ExecutionContext): Future[CassandraOffsetRepository] = {
    
    def createRepositoryFromOffset(offset: Offset) = new CassandraOffsetRepository(session, insertStatement, eventProcessorId, tag, offset)
    def resultSetToRepository(resultSet: ResultSet) = createRepositoryFromOffset(extractOffsetFromResultSet(resultSet))

    val tableName = "appointmentquery.offsetStore"
    session.executeAsync(s"SELECT timeUuidOffset, sequenceOffset FROM $tableName WHERE eventProcessorId = ? AND tag = ?", eventProcessorId, tag)   // delivers an ResultSetFuture (.google.ListenableFuture)
      .map(resultSetToRepository)
  }

  private def extractOffsetFromResultSet(resultSet: ResultSet): Offset = extractOffset(resultSet.all().asScala.headOption)
  
  private def extractOffset(maybeRow: Option[Row]): Offset =
    maybeRow match {
      case Some(row) =>
        val uuid = row.getUUID("timeUuidOffset")
        if (uuid != null) TimeBasedUUID(uuid)
        else if (row.isNull("sequenceOffset")) NoOffset
        else Sequence(row.getLong("sequenceOffset"))
      
      case None => NoOffset
    }
}

object CassandraConversions {
  implicit def resultSetFutureToScala(f: ResultSetFuture): Future[ResultSet] = {
    val p = Promise[ResultSet]()
    Futures.addCallback(f,
      new FutureCallback[ResultSet] {
        def onSuccess(r: ResultSet) = p success r

        def onFailure(t: Throwable) = p failure t
      })
    p.future
  }

}
