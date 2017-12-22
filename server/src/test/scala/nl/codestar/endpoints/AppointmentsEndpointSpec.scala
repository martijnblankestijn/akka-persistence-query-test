package nl.codestar.endpoints

import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit.HOURS
import java.time.{LocalDateTime, OffsetDateTime}
import java.util.UUID
import java.util.UUID.fromString

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.HttpMethods.{DELETE, GET, POST}
import akka.http.scaladsl.model.StatusCodes.{Created, NoContent, NotFound, OK}
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import nl.codestar.persistence.phantom.Appointment
import org.scalatest.OptionValues._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import org.scalatest.{FunSpec, Matchers}

import scala.collection.immutable.Seq

class AppointmentsEndpointSpec
    extends FunSpec
    with ScalaFutures
    with Matchers
    with JsonProtocol {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  override implicit val patienceConfig = PatienceConfig(
    timeout = scaled(Span(2500, Millis)),
    interval = scaled(Span(250, Millis)))
  val path = "/appointments"
  val uri =
    Uri.from(scheme = "http", host = "localhost", port = 8080, path = path)

  describe("Appointment endpoint") {
    it("should post, delete, get a request and query all requests") {
      val fResponse = createAppointment

      whenReady(fResponse) { resp =>
        resp.status shouldBe Created
        val newAppointmentUri = extractLocationUri(resp)
        val id = extractUuid(newAppointmentUri)

        newAppointmentUri.path.startsWith(Path(path)) shouldBe true

        Thread.sleep(10000) // for now sleep as to give the Query database the time to catch up

        // there should be some appointments
        whenReady(Http().singleRequest(HttpRequest(GET, uri))) { resp =>
          resp.status shouldBe OK
          whenReady(Unmarshal(resp.entity).to[Array[Appointment]]) {
            appointments =>
              appointments.map(_.id) should contain(id)
          }
        }

        whenReady(Http().singleRequest(HttpRequest(GET, newAppointmentUri))) {
          resp =>
            resp.status shouldBe OK

            whenReady(
              Http().singleRequest(HttpRequest(DELETE, newAppointmentUri))) {
              resp =>
                resp.status shouldBe NoContent

                Thread.sleep(10000) // for now sleep as to give the Query database the time to catch up
                whenReady(
                  Http().singleRequest(HttpRequest(GET, newAppointmentUri))) {
                  resp =>
                    resp.status shouldBe NotFound
                }
            }
        }
      }
    }

    it("should reassign an appointment") {
      val newAppointment = createAppointment
      whenReady(newAppointment) { resp =>
        resp.status shouldBe Created

        val appointmentUri = extractLocationUri(resp)
        val id = extractUuid(appointmentUri)
        val reassigned = Http().singleRequest(
          HttpRequest(
            POST,
            uri = appointmentUri + "/reassign",
            Seq[HttpHeader](),
            HttpEntity(
              `application/json`,
              s"""{"appointmentId": "$id", "advisorId": "5f268c64-86dc-4996-90ce-626f4efa0627" }""")
          ))

        whenReady(reassigned) { reassignedResp =>
          reassignedResp.status shouldBe NoContent

          whenReady(Http().singleRequest(HttpRequest(GET, appointmentUri))) {
            resp =>
              resp.status shouldBe OK
              whenReady(Unmarshal(resp.entity).to[Appointment]) { app =>
                app.advisorId shouldBe fromString(
                  "5f268c64-86dc-4996-90ce-626f4efa0627")
              }

          }
        }
      }
    }

    it("should move an appointment") {
      val newAppointment = createAppointment
      whenReady(newAppointment) { resp =>
        resp.status shouldBe Created

        val appointmentUri = extractLocationUri(resp)
        val id = extractUuid(appointmentUri)
        val newStart = tomorrow.plusDays(1)
        val moveEntity =
          s"""{ "appointmentId": "$id", 
            |  "branchId": "3d2b0af2-d60c-4f1e-bf21-c82c067f6fa6",
            |	 "advisorId": "aa9e555c-5f5e-489d-8ed1-1f05a85ef999",
            |	 "start": "${newStart}",
            |	 "roomId": "aa9e555c-5f5e-489d-8ed1-1f05a85ef999" }""".stripMargin
        val move = Http().singleRequest(
          HttpRequest(POST,
                      uri = appointmentUri + "/move",
                      Seq[HttpHeader](),
                      HttpEntity(`application/json`, moveEntity)))

        whenReady(move) { movedResp =>
          movedResp.status shouldBe NoContent

          whenReady(Http().singleRequest(HttpRequest(GET, appointmentUri))) {
            changedResponse =>
              changedResponse.status shouldBe OK
              whenReady(Unmarshal(changedResponse.entity).to[Appointment]) {
                changedAppointment =>
                  changedAppointment.advisorId shouldBe fromString(
                    "aa9e555c-5f5e-489d-8ed1-1f05a85ef999")
                  val time = LocalDateTime.of(2017, 4, 3, 14, 45)
                  changedAppointment.start shouldBe newStart
                  changedAppointment.branchId shouldBe fromString(
                    "3d2b0af2-d60c-4f1e-bf21-c82c067f6fa6")
                  changedAppointment.roomId shouldBe Some(
                    fromString("aa9e555c-5f5e-489d-8ed1-1f05a85ef999"))
              }

          }
        }
      }
    }

  }

  private def extractUuid(newAppointmentUri: Uri) = {
    fromString(newAppointmentUri.path.reverse.head.toString)
  }

  private def extractLocationUri(resp: HttpResponse): Uri = {
    resp.header[Location].value.uri
  }

  private def createAppointment = {
    val uuid = UUID.randomUUID()
    val request =
      s"""{ "appointmentId": "$uuid",
      "branchId": "dd9e555c-5f5e-489d-8ed1-1f05a85ef891",
	    "advisorId": "aa9e555c-5f5e-489d-8ed1-1f05a85ef111",
	    "start": "$tomorrow",
	    "duration": "30 minutes"} """
    println(request)
    Http().singleRequest(
      HttpRequest(POST,
                  uri,
                  Seq[HttpHeader](),
                  HttpEntity(`application/json`, request.stripMargin)))
  }

  private def tomorrow = {
    LocalDateTime.now.plusDays(1).truncatedTo(HOURS)
  }
}
