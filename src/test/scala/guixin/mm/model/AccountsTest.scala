package guixin.mm.model

import guixin.mm.UnitSpec

class AccountsTest extends UnitSpec {
  implicit val executionContext = scala.concurrent.ExecutionContext.global

  val db = DBSchema.createDatabase

  test("getting accounts") {
    val records = for (Some(account) <- db.getAccount(1);
                       records <- db.accountRecords(account.id)) yield records

    val got = records.futureValue
    println(got.mkString("\n"))
  }
}
