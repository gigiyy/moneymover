package guixin.mm

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import guixin.mm.AccountService._
import guixin.mm.model.DBSchema
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike, Matchers}

import scala.concurrent.duration._

class AccountServiceTest(_system: ActorSystem) extends TestKit(_system) with FunSuiteLike
  with Matchers with BeforeAndAfterAll with ImplicitSender {

  def this() = this(ActorSystem("AccountServiceSuite"))

  override def afterAll: Unit = {
    concurrent.Await.result(system.terminate(), Duration.Inf)
    ()
  }

  val db = DBSchema.createDatabase
  val service = system.actorOf(AccountService.props(db), "credit")

  test("credit to user") {
    service ! Credit(10, 500, "usd")
    expectMsg(Ok("Credited usd 500.0 successfully to user 10"))
    expectNoMessage(100.millis)
    service ! WorkingIds
    expectMsg(Set.empty)

  }

  test("credit and debit") {
    service ! Credit(11, 500, "usd")
    service ! Debit(11, 400, "usd")
    expectMsg(Ok("Credited usd 500.0 successfully to user 11"))
    expectMsg(Ok("Debited usd 400.0 successfully from user 11"))
    expectNoMessage(100.millis)
    service ! WorkingIds
    expectMsg(Set.empty)
  }

  test("transfer between accounts") {
    service ! Credit(12, 1000, "usd")
    service ! Debit(12, 100, "usd")
    service ! Trans(Debit(12, 400, "usd"), Credit(13, 40000, "jpy"))

    expectMsg(Ok("Credited usd 1000.0 successfully to user 12"))
    expectMsg(Ok("Debited usd 100.0 successfully from user 12"))
    expectMsg(Ok("Transferred usd 400.0 from user 12 to user 13 (jpy 40000.0)"))
    expectNoMessage(100.millis)
    service ! WorkingIds
    expectMsg(Set.empty)
  }

  test("debit account not existing") {
    service ! Debit(14, 100, "usd")
    expectMsg(Err("Account not found for user 14 of usd"))
    expectNoMessage(100.millis)
    service ! WorkingIds
    expectMsg(Set.empty)
  }
}
