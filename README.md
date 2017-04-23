# Study project for CQRS and Event Sourcing with Akka Persistence 
## Domain

The domain is a business in which appointments with consultants (advisors) can be scheduled.
A Calendar consists of a number of appointments. 
There a number of events defined for appointments.
These are:

- creation
- cancellation
- moving an appointment to a different location or different time
- reassigning an appointment to a different  consultant
 
## Used technologies

- [Akka](http://akka.io/)
- [Akka Persistence](http://doc.akka.io/docs/akka/current/scala/persistence.html)
- [Akka HTTP](http://doc.akka.io/docs/akka-http/current/scala.html)
- [Protobuf](https://developers.google.com/protocol-buffers/docs/proto3)
- [phantom](http://outworkers.com/blog/post/a-series-on-nl.codestar.persistence.phantom-part-1-getting-started-with-nl.codestar.persistence.phantom)
- [Cats](http://typelevel.org/cats/)
- [Cassandra](http://cassandra.apache.org/)

### Akka, Akka HTTP and Akka Persistence
Akka HTTP is used for the server-side and client-side (tests) HTTP stack for this study project.
Akka Persistence is used for event sourcing.

### Protobuf
"Protocol buffers are a language-neutral, platform-neutral extensible mechanism for serializing structured data.".
The events for an appointment that are being stored in the event store, are defined with Google protobuf (version 3).

The serialized events are:

- AppointmentCreated
- AppointmentReassigned
- AppointmentCancelled
- AppointmentMoved

### phantom
Phantom is a "schema safe, type-safe, reactive Scala driver for Cassandra/Datastax Enterprise". 
This is used to access the table in the query database. 

### Cats
Cats is a "lightweight, modular, and extensible library for functional programming". It is used only for validation.

### Cassandra
Apache Cassandra is used for the event store and for the query model.

## Running
First start docker-machine to get Cassandra running with `docker-machine start default`.
Next is exporting the running docker-machine environment variables `eval "$(docker-machine env default)"`.
 
Then start Cassandra with `src/test/docker/run-cassandra.sh` and create the keyspace for the Query-side with 
`src/test/docker/create-tables.sh`.
`src/test/docker/run-cql.sh` gives you access to the Cassandra cql.


Run the Server `nl.codestar.persistence.Server`. This starts up the Akka Actor system with the persistence actors.

Run the event processor which will watch the new events in the event store and will process the events to the query model.
Run `nl.codestar.query.EventProcesserApplication` to start the processing of the events.

The test `nl.codestar.endpoints.AppointmentsEndpointSpec` has some crude tests for the most important functionality.

## Errors
The solution for getting the error 'Caused by: java.lang.ClassNotFoundException: scala.reflect.runtime.package$'.
when running the ReadJournalClient from IntelliJ was fixed by adding the scala-reflect dependency with a compile scope.

## Implementation

### Offset store
Borrowed the idea from Lagom. See CassandraOffsetStore.