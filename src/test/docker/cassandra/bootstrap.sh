#!/bin/bash
set -e

# If we're starting DSE
if [ "$1" = 'cassandra' ]; then
  # See if we've already completed bootstrapping
  if [ ! -f /cassandra_bootstrapped ]; then
    echo 'Setting up Cassandra'

    # Invoke the entrypoint script to start DSE as a background job and get the pid
    echo '=> Starting Cassandra'
    /docker-entrypoint.sh "$@" &
    cassandra_pid="$!"

    # Wait for port 9042 (CQL) to be ready in the specified time
    echo '=> Waiting for Cassandra to become available'
    /wait-for-it.sh -t 240 cassandra:9042
    echo '=> Cassandra is available'

    # Create the keyspace if necessary
    echo '=> Ensuring keyspace is created'
    cqlsh -f /opt/cql/001_create_query_tables.cql 127.0.0.1 9042


    # Shutdown DSE after bootstrapping to allow the entrypoint script to start normally
    echo '=> Shutting down Cassandra after bootstrapping'
    kill -s TERM "$cassandra_pid"

    # Cassandra will exit with code 143 (128 + 15 SIGTERM) once stopped
    set +e
    wait "$cassandra_pid"
    if [ $? -ne 143 ]; then
      echo >&2 'Cassandra setup failed'
      exit 1
    fi
    set -e

    # Don't bootstrap next time we start
    touch /cassandra_bootstrapped

    # Now allow Cassandra to start normally below
    echo 'Cassandra has been setup, starting Cassandra normally'
  fi
fi

# Run the main entrypoint script from the base image
exec /docker-entrypoint.sh "$@"