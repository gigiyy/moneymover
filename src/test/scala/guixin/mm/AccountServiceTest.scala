package guixin.mm

import guixin.mm.model.DBSchema
import akka.actor.testkit.typed.scaladsl._

import scala.concurrent.Future

class AccountServiceTest extends UnitSpec {
  val db = DBSchema.createDatabase

  import AccountService._

  test("credit user") {
    val tester = TestInbox[Future[String]]()
    val service = BehaviorTestKit(AccountService(10, db, tester.ref))
    service.ref ! Credit(3, 500, "usd")
    service.runOne()
    val result :: Nil = tester.receiveAll()

    assert(result.futureValue === "Credited usd 500.0 successfully to user 3")
  }
}
