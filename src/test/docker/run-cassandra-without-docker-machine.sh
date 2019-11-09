#!/usr/bin/env bash
# See https://hub.docker.com/_/cassandra/ for details on the different tags
MACHINE_NAME=default
IMAGE=cassandra:3
IMAGE_NAME=akka-persist-appointment-cassandra

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
docker run --name akka-persist-appointment-cassandra --network akka-persistence-appointment_default -d cassandra:3.11.5

docker run -it --network akka-persistence-appointment_default  --rm -v $(pwd)/src/main/cql/001_create_query_tables.cql:/init/001_create_query_tables.cql cassandra:latest cqlsh --debug -f /init/001_create_query_tables.cql akka-persist-appointment-cassandra
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
