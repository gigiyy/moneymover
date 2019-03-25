package guixin.mm.model

import java.time._

object account {

  case class Account(id: Int, userId: Int, balance: Double, currency: String)

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