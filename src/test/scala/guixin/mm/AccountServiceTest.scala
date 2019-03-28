package guixin.mm

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import guixin.mm.AccountService.{Credit, Debit, Trans, WorkingIds}
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
    expectMsg("Credited usd 500.0 successfully to user 10")
    expectNoMessage(100.millis)
    service ! WorkingIds
    expectMsg(Set.empty)

  }

  test("credit and debit") {
    service ! Credit(11, 500, "usd")
    service ! Debit(11, 400, "usd")
    expectMsg("Credited usd 500.0 successfully to user 11")
    expectMsg("Debited usd 400.0 successfully from user 11")
    expectNoMessage(100.millis)
    service ! WorkingIds
    expectMsg(Set.empty)
  }

  test("transfer between accounts") {
    service ! Credit(12, 1000, "usd")
    service ! Debit(12, 100, "usd")
    service ! Trans(Debit(12, 400, "usd"), Credit(13, 40000, "jpy"))

    expectMsg("Credited usd 1000.0 successfully to user 12")
    expectMsg("Debited usd 100.0 successfully from user 12")
    expectMsg("Transferred usd 400.0 from user 12 to user 13 (jpy 40000.0)")
    expectNoMessage(100.millis)
    service ! WorkingIds
    expectMsg(Set.empty)
  }
}
