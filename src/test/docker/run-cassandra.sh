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
MACHINE_NAME=default
IMAGE=cassandra:3.0
# alternative image cassandra:2.2.8
IMAGE_NAME=akka-persist-appointment-cassandra

status=$(docker-machine status $MACHINE_NAME)
if [ $? -eq 0 ]
then
  echo "Docker machine $MACHINE_NAME exists"
else
  echo "Machine [$MACHINE_NAME] does not exists, run the following command to start the docker-machine"; 
  echo "   docker-machine create --driver virtualbox $MACHINE_NAME" 
  exit 1
fi
if [ "$status" == "Stopped" ]
then 
  echo "Docker machine $MACHINE_NAME is stopped, starting it now"
  docker-machine start default || echo "Error $? starting machine $MACHINE_NAME"; exit 1;
else
  echo "Docker machine is running"
fi
# Does this work exporting this to the environment of the shell?
eval "$(docker-machine env default)"

# Is there already a container with the image name, if so remove it and start with a fresh one
dockerInstance=$(docker ps -aq --filter name=$IMAGE_NAME)
if [ "$dockerInstance" == "" ]
then
    echo "No docker instance for image $IMAGE yet, running a new one"
    docker run --name $IMAGE_NAME -p 9042:9042 -d $IMAGE
else
    echo "Docker instance found, removing container now"
    docker rm -f $dockerInstance
    echo "Docker instance starting"
    docker run --name $IMAGE_NAME -p 9042:9042 -d $IMAGE
fi

# For now just run cqlsh from the local machine, have to search for a nicer approach
# cqlsh 
#IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' $IMAGE_NAME)
IP=$(docker-machine ip $MACHINE_NAME)
sleep 20
echo "Waiting for start of cassandra op ip-addres $IP"
cqlsh --debug -f src/main/cql/001_create_query_tables.cql $IP
result=$?
if [ $? -eq 0 ]
then
    echo "Start script has been applied. $result"
else
     echo "Creating the tables failed. Result = $result"
fi
