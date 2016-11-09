#!/usr/bin/env bash
# "Cannot connect to the Docker daemon. Is the docker daemon running on this host?"
#
# Create the virtual machine
# $> docker-machine create --driver virtualbox default
#
# or start it when it is created 
# $> docker-machine start default
#
# To access the environment variables
# $> eval "$(docker-machine env default)"
#
#
#
# See https://hub.docker.com/_/cassandra/ for details on the different tags
docker run --name akka-persist-appointment-cassandra -d cassandra:2.2.8