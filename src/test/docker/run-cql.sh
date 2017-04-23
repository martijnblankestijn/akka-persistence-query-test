#!/usr/bin/env bash
#docker run -it --link akka-persist-appointment-cassandra:cassandra --rm cassandra:2.2.8 cqlsh cassandra
docker run -it --link akka-persist-appointment-cassandra:cassandra --rm cassandra:3.0 cqlsh cassandra