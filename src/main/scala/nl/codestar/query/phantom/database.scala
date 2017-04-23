package nl.codestar.query.phantom

import com.outworkers.phantom.connectors.{CassandraConnection, ContactPoints}
import com.outworkers.phantom.database.Database
import com.outworkers.phantom.dsl.KeySpaceDef
import com.typesafe.config.ConfigFactory
import nl.codestar.query.phantom.Connector.connector

import scala.collection.JavaConverters._


object Connector {
  private val config = ConfigFactory.load()

  private val hosts = config.getStringList("cassandra.contact-points").asScala
  private val keyspace = config.getString("cassandra.keyspace")

  lazy val connector: CassandraConnection = ContactPoints(hosts).keySpace(keyspace)
}

class AppointmentsDatabase(val keyspace: KeySpaceDef) extends Database[AppointmentsDatabase](keyspace) {
  object appointments extends AppointmentRepository with keyspace.Connector
  object appointmentsByBranchId extends AppointmentByBranchIdRepository with keyspace.Connector
}

object AppointmentsDatabase extends AppointmentsDatabase(connector)
