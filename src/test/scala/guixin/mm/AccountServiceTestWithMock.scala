package guixin.mm

import java.io.IOException

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import guixin.mm.AccountService._
import guixin.mm.model.DAO
import guixin.mm.model.account.Money
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike, Matchers}

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._

class AccountServiceTestWithMock(_system: ActorSystem) extends TestKit(_system) with FunSuiteLike
  with Matchers with BeforeAndAfterAll with ImplicitSender with MockFactory {

  def this() = this(ActorSystem("AccountServiceMockSuite"))

  override def afterAll: Unit = {
    concurrent.Await.result(system.terminate(), Duration.Inf)
    ()
  }


  test("credit user mock") {

    val db = mock[DAO]
    val service = system.actorOf(AccountService.props(db), "credit")

    val creditPromise = Promise[String]()
    (db.credit _).expects(21, Money(300.0, "usd")).returning(creditPromise.future)

    service ! Credit(21, 300, "usd")

    expectNoMessage(100.millis)
    creditPromise.success("credited 21")
    expectMsg(Ok("credited 21"))
  }

  test("credit and debit mock") {
    val db = mock[DAO]
    val service = system.actorOf(AccountService.props(db), "credit_debit")

    val creditPromise = Promise[String]()
    (db.credit _).expects(22, Money(500.0, "jpy")).returning(creditPromise.future)

    service ! Credit(22, 500, "jpy")
    service ! Debit(22, 100, "jpy")

    expectNoMessage(100.millis)
    val debitPromise = Promise[String]()
    debitPromise.success("debit 22")
    (db.debit _).expects(22, Money(100.0, "jpy")).returning(debitPromise.future)

    creditPromise.success("credit 22")
    expectMsg(Ok("credit 22"))
    expectMsg(Ok("debit 22"))
  }

  test("transfer mock, and make sure unaffected transfers will finished without caching") {
    val db = mock[DAO]
    val service = system.actorOf(AccountService.props(db), "transfer")

    val creditPromise = Promise[String]()
    (db.credit _).expects(23, Money(500.0, "eur")).returning(creditPromise.future)
    (db.credit _).expects(24, Money(100.0, "jpy")).returning(Future.successful("credit 24"))
    (db.transfer(_: Int, _: Int, _: Money, _: Money)).expects(23, 25, Money(200.0, "eur"), Money(20000.0, "jpy"))
      .returning(Future.successful("transfer 23->25"))

    service ! Credit(23, 500, "eur")
    service ! Credit(24, 100, "jpy") // not affected by previous unrelated transaction
    service ! Trans(Debit(23, 200, "eur"), Credit(25, 20000, "jpy"))

    expectMsg(Ok("credit 24"))
    expectNoMessage(100.millis)
    creditPromise.success("credit 23")
    expectMsg(Ok("credit 23"))
    expectMsg(Ok("transfer 23->25"))
  }

  test("db failed with unknown error") {
    val db = mock[DAO]
    val service = system.actorOf(AccountService.props(db), "sysError")

    (db.credit _).expects(26, Money(1000, "gui")).returning(Future.failed(new IOException("db down")))

    service ! Credit(26, 1000, "gui")
    expectMsg(SysErr("db down"))
  }

}