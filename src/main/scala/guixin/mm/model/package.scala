package guixin.mm.model

import java.time._

object account {

  class Money(val amount: Double, val currency: String) {

    def +(that: Money): Money = {
      require(currency == that.currency, s"can't add different currencies $currency with ${that.currency}")
      Money(amount + that.amount, currency)
    }

    def -(that: Money): Money = {
      require(amount >= that.amount)
      require(currency == that.currency, s"can't subtract different currencies $currency with ${that.currency}")
      Money(amount - that.amount, currency)
    }
  }

  object Money {
    def apply(amount: Double, currency: String) = new Money(amount, currency)

    def usd(amount: Double): Money = Money(amount, "usd")

    def eur(amount: Double): Money = Money(amount, "eur")

    def jpy(amount: Double): Money = Money(amount, "jpy")
  }

  case class Account(id: Int, userId: Int, balance: Double, currency: String) {
    def getBalance: Money = Money(balance, currency)
  }

  // TODO leaving out the Instant <=> Timestamp mapping for now.
  // Transfer/Credit/Debit
  case class Transfer(id: Int,
                      from: Option[Int], to: Option[Int],
                      fromAmount: Option[Double], toAmount: Option[Double],
                      comment: Option[String]) {
    def getRecords: List[Record] = {
      List(
        from.map(account => Debit(id, account, fromAmount.getOrElse(0), to, comment)),
        to.map(account => Credit(id, account, toAmount.getOrElse(0), from, comment))
      ).flatten
    }
  }

  sealed trait Record {
    def id: Int

    def accountId: Int
  }

  case class Credit(id: Int, accountId: Int, amount: Double, fromAccount: Option[Int],
                    comment: Option[String]) extends Record

  case class Debit(id: Int, accountId: Int, amount: Double, toAccount: Option[Int],
                   comment: Option[String]) extends Record

}

object fx {

  case class Currency(id: Int, name: String)

  case class Rate(from: Int, to: Int, rate: Double, time: Instant)

}

object user {

  case class User(id: Int, name: String)

}