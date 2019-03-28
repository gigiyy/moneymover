package guixin.mm

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import guixin.mm.AccountService._
import guixin.mm.model.DBSchema

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Success

object Server extends App {

  val PORT = 8080

  implicit val actorSystem: ActorSystem = ActorSystem("money-mover")
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val timeout: Timeout = Timeout(5.seconds)

  import actorSystem.dispatcher

  import scala.concurrent.duration._

  scala.sys.addShutdownHook(() -> shutdown())

  val db = DBSchema.createDatabase
  val service = actorSystem.actorOf(AccountService.props(db))

  def enclose(message: String) = s"$message\n"

  def mapResult(f: Future[Result]) = {
    onComplete(f) {
      case Success(Ok(message)) => complete(HttpResponse(StatusCodes.OK, entity = enclose(message)))
      case Success(Err(message)) => complete(HttpResponse(StatusCodes.BadRequest, entity = enclose(message)))
      case Success(SysErr(message)) => complete(HttpResponse(StatusCodes.InternalServerError, entity = enclose(message)))
      case util.Failure(_) => complete(HttpResponse(StatusCodes.InternalServerError, entity = enclose("UNKNOWN")))
    }
  }

  val route: Route = {
    path("hello") {
      get {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
      }
    } ~ {
      path("credit") {
        post {
          parameters(('user.as[Int], 'amount.as[Double], 'currency)).as(Credit) { credit =>
            mapResult((service ? credit).mapTo[Result])
          }
        }
      }
    } ~ {
      path("debit") {
        post {
          parameters(('user.as[Int], 'amount.as[Double], 'currency)).as(Debit) { debit =>
            mapResult((service ? debit).mapTo[Result])
          }
        }
      }
    } ~ {
      path("transfer") {
        post {
          parameters(('from.as[Int], 'to.as[Int], 'amount.as[Double], 'currency)) { (from, to, amount, currency) =>
            mapResult((service ? Trans(Debit(from, amount, currency), Credit(to, amount, currency))).mapTo[Result])
          }
        }
      }
    }
  }

  Http().bindAndHandle(route, "0.0.0.0", PORT)
  println(s"open a browser with URL: http://localhost:$PORT")


  def shutdown(): Unit = {
    actorSystem.terminate()
    Await.result(actorSystem.whenTerminated, 30 seconds)
  }
}

