package guixin.mm.model

import guixin.mm.UnitSpec
import guixin.mm.model.account.{Credit, Debit, Money}

class AccountsTest extends UnitSpec {
  implicit val executionContext = scala.concurrent.ExecutionContext.global

  val db = DBSchema.createDatabase

  test("getting accounts") {
    val records = for (Some(account) <- db.getAccount(1);
                       records <- db.accountRecords(account.id)) yield records

    val got = records.futureValue
    println(got.mkString("\n"))
  }

  test("credit accounts") {
    db.credit(3, Money.jpy(500)).futureValue

    val Some(account) = db.getAccount(3, "jpy").futureValue
    assert(account.balance === 500)

    val record = db.accountRecords(account.id).futureValue
    val Credit(_, accountId, amount, _, _) = record.head
    assert(accountId === account.id)
    assert(amount === 500.0)
  }

  test("debit from account not exists") {
    val result = db.debit(3, Money.usd(100))
    whenReady(result.failed) { e =>
      e shouldBe a[IllegalArgumentException]
      e should have message "Account not found for user 3 of usd"
    }
  }

  test("over debit from account") {
    db.credit(4, Money.usd(100)).futureValue

    val result = db.debit(4, Money.usd(500))
    whenReady(result.failed) { e =>
      e shouldBe a[IllegalStateException]
      e should have message "not sufficient balance for user 4 of usd"
    }
  }

  test("debit accounts") {
    db.credit(5, Money.usd(1000)).futureValue
    db.debit(5, Money.usd(500)).futureValue

    val Some(account) = db.getAccount(5, "usd").futureValue
    assert(account.balance === 500.0)

    val records = db.accountRecords(account.id).futureValue.sortBy(_.id)
    val Debit(_, accountId, amount, _, _) = records.last
    assert(accountId == account.id)
    assert(amount === 500.0)
  }

  test("transfer money from one user to another") {
    db.credit(6, Money.usd(1000)).futureValue
    db.credit(7, Money.usd(100)).futureValue

    db.transfer(6, 7, Money.usd(400)).futureValue

    val Some(fromAccount) = db.getAccount(6, "usd").futureValue
    val Some(toAccount) = db.getAccount(7, "usd").futureValue
    assert(fromAccount.balance === 600.0)
    assert(toAccount.balance === 500.0)

    val toRecords = db.accountRecords(toAccount.id).futureValue.sortBy(_.id)
    val Credit(_, toAccountId, amount, Some(fromAccountId), _) = toRecords.last
    assert(toAccountId === toAccount.id)
    assert(fromAccountId === fromAccount.id)
    assert(amount === 400.0)


    val fromRecords = db.accountRecords(fromAccount.id).futureValue.sortBy(_.id)
    val Debit(_, debitFromId, debitAmount, Some(debitToId), _) = fromRecords.last
    assert(debitFromId === fromAccount.id)
    assert(debitToId === toAccount.id)
    assert(debitAmount === 400.0)
  }
}
