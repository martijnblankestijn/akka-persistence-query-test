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

### Phantom
Phantom is a "schema safe, type-safe, reactive Scala driver for Cassandra/Datastax Enterprise". 
This is used to access the table in the query database. 

### Cats
Cats is a "lightweight, modular, and extensible library for functional programming". It is used only for validation.

### Cassandra
Apache Cassandra is used for the event store and for the query model. 
For local testing it can be started in a Docker container which also creates the tables for the query-model. 

## Building
As most project sbt is used. Handy tasks to remember:

- `sbt dependencyUpdates` - checks for newer versions of dependencies

## Installing and run with Docker
Run `sbt docker:publishLocal`. 
"SBT uses the task named publishLocal to publish assets to local Ivy/Maven repositories".
If you get the following error:

```
error] Cannot connect to the Docker daemon at unix:///var/run/docker.sock. Is the docker daemon running?
``` 

Start docker-machine and make sure the environment variables are known in your shell, see [Running](#running)

The docker-compose.yml has the Docker compose Configuration.
With `docker-compose scale akka-nodes=` can be scaled up or down. 
`docker-compose up` will get the whole environment up and running
 
nginx is configured to run on port 80, so with `docker-machine env` figure out what the ip-address is of the virtual machine(on Mac).
And (probably hit) [http://192.168.1.100](http://192.168.1.100).

### Building the cassandra container
This should be automated in a later moment:
`docker build -t mblankestijn/cassandra:3.0  .`

## Running the Server from IntellJ with Cassandra/Zoo keeper in Docker containers
First start docker-machine to get Cassandra running with `docker-machine start default` (tested with docker-machine 0.13.0).
Next is exporting the running docker-machine environment variables `eval "$(docker-machine env default)"`.
 
Then start Cassandra with `src/test/docker/run-cassandra.sh` and create the keyspace for the Query-side with 
`src/test/docker/create-tables.sh`.
`src/test/docker/run-cql.sh` gives you access to the Cassandra cql.

Start Kafka and [Zookeeper](https://docs.confluent.io/current/cp-docker-images/docs/quickstart.html#installing-running-docker) with
```
docker run -d \
       --net=host \
       --name=zookeeper \
       -e ZOOKEEPER_CLIENT_PORT=32181 \
       confluentinc/cp-zookeeper:3.3.0
```
and
```
 docker run -d \
    --net=host \
    --name=kafka \
    -e KAFKA_ZOOKEEPER_CONNECT=localhost:32181 \
    -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:29092 \
    -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
    confluentinc/cp-kafka:3.3.0
```

Run the Server `nl.codestar.persistence.Server`. This starts up the Akka Actor system with the persistence actors.

Run the event processor which will watch the new events in the event store and will process the events to the query model.
Run `nl.codestar.query.EventProcesserApplication` to start the processing of the events.

The test `nl.codestar.endpoints.AppointmentsEndpointSpec` has some crude tests for the most important functionality.

## Performance testing
sbt performancetest/gatling:test // [subproject]/[gatling-plugin]:[task]


## TODO
Change default for cassandra persistence plugin 'log-queries = off' to on. 

For now the case classes used for command inherit from AppointmentCommand which has an appointmentId.
This appointmentId is used in the Request entity and in the url of the Akka HTTP interface. 
This is unncessary and not REST-like.

## Errors
The solution for getting the error 'Caused by: java.lang.ClassNotFoundException: scala.reflect.runtime.package$'.
when running the ReadJournalClient from IntelliJ was fixed by adding the scala-reflect dependency with a compile scope.

## Implementation

### Modules

- API: The Akka HTTP Endpoints for querying, creating and updating the appointments
- appointments: The PersistentActor for the appointments
- domain: Shared domain classes and protobuf definitions for the events
- persistence: Storing the events in the Query/Projection database
- query: Processing appointment events to the Query/Projection database of the appointments
- query-kafka: alternative way of processing (Not yet implemented)

- integrationtest: Integration test (not completely finished yet)
- performancetest: Basic Gatling script

### Offset store
Borrowed the idea from Lagom. See CassandraOffsetStore.


## References

- [Akka cluster in Docker](https://hackernoon.com/akka-cluster-in-docker-a-straight-forwards-configuration-b6deea32752d)
- [Akka behind NAT]](http://doc.akka.io/docs/akka/2.5.1/scala/remoting.html#remote-configuration-nat)