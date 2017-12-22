import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.HOURS
import java.util.UUID

import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport._
import org.scalatest.{BeforeAndAfter, FunSpec}

class SomeScalaTest extends FunSpec with BeforeAndAfter {
  describe("appointments") {
    it("gives a 404 if not found") {
      given().
        port(8080).
        when().
        get("/appointments/3d2b0af2-d60c-4f1e-bf21-c82c067f6fa9").
        Then().
        statusCode(404)
    }

    it("posts and retrieves an appointment") {

      val uuid = UUID.randomUUID()
      val tomorrow = LocalDateTime.now.plusDays(1).truncatedTo(HOURS)
      given().
        port(8080).
        contentType("application/json").
        body(
          s"""
         { "appointmentId": "$uuid",
            "branchId": "dd9e555c-5f5e-489d-8ed1-1f05a85ef891",
            "advisorId": "aa9e555c-5f5e-489d-8ed1-1f05a85ef111",
            "start": "$tomorrow",
            "duration": "30 minutes"
        } """.stripMargin).
        when().
        post("/appointments").
      Then().
        statusCode(201)
    }

  }

}
