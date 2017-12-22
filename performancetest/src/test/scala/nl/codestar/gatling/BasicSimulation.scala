package nl.codestar.gatling

import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.time.temporal.ChronoUnit.HOURS
import java.util.UUID
import java.util.UUID.randomUUID

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._
import scala.util.Random._


object Create {
  val idFeeder = Iterator.continually(Map("id" -> randomUUID()))
  val advisorIdFeeder = Iterator.continually(Map("advisorId" -> randomUUID()))
  val dateFeeder = Iterator.continually(Map("date" -> now.plusDays(1).plusDays(nextInt(14)).truncatedTo(HOURS)))

  val create =

    feed(idFeeder)
      .feed(dateFeeder)
      .feed(advisorIdFeeder)
      .exec(http("create appointment")
        .post("")
        .body(StringBody(
          """{ "appointmentId": "${id}",
              "branchId": "dd9e555c-5f5e-489d-8ed1-1f05a85ef891",
              "advisorId": "aa9e555c-5f5e-489d-8ed1-1f05a85ef111",
              "start": "${date}",
              "duration": "30 minutes"} """))
        .check(status.is(201))  
        .asJSON)
      .exec(http("get the appointment")
        .get("/${id}")
        .check(status.is(200))  
      )
      .exec(http("move appointment")
        .post("/${id}/reassign")
        .body(StringBody(
          """{"appointmentId": "${id}", "advisorId": "${advisorId}" }"""
        )).check(status.is(204)).asJSON  
      )  
}

class BasicSimulation extends Simulation {
  val host = "192.168.99.100"
  val port = 80
  val httpConf = http.baseURL(s"http://$host:$port/appointments")


  val scn = scenario("BasicTest").repeat(1, " n") {
    exec(http("get appointments").get(""))
      .pause(1 seconds)
      .exec(Create.create)
  }


  setUp(
    scn.inject(rampUsers(10) over 10.seconds)
  ).protocols(httpConf)

}
