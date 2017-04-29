#!/usr/bin/env bash
file=src/main/cql/001_create_query_tables.cql
echo Creating the query tables $file
cqlsh -f $file $(docker-machine ip default)