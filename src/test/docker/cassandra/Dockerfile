FROM cassandra:3.0.15

COPY [ "cql", "/opt/cql"]
COPY [ "bootstrap.sh", "lib/wait-for-it/wait-for-it.sh", "/" ]

RUN chmod +x /bootstrap.sh /wait-for-it.sh

# Set the entrypoint to the bootstrap script
ENTRYPOINT [ "/bootstrap.sh" ]

# Run Cassandra in search mode in the foreground by default
CMD [ "cassandra", "-f" ]