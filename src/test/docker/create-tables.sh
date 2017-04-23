#!/usr/bin/env bash
cqlsh -f src/main/cql/001_create_query_tables.cql $(docker-machine ip default)