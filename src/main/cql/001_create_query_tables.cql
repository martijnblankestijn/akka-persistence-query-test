CREATE KEYSPACE IF NOT EXISTS appointmentquery WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };

use appointmentquery;

CREATE TABLE IF NOT EXISTS appointment(
  id uuid,
  state text,
  advisorid uuid,
  roomid uuid,
  branchid uuid,
  start timestamp,
  PRIMARY KEY(id)
);


CREATE TABLE IF NOT EXISTS appointment_by_branchid (
  branchid uuid,
  yearmonth text,
  appointmentid uuid,
  state text,
  advisorid uuid,
  roomid uuid,
  start timestamp,
  PRIMARY KEY( (branchid, yearmonth), appointmentid)
);

CREATE TABLE IF NOT EXISTS offsetStore (
  eventProcessorId text,
  tag text, 
  timeUuidOffset timeuuid, 
  sequenceOffset bigint,
  PRIMARY KEY (eventProcessorId, tag)
);
