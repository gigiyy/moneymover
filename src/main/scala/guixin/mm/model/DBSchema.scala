package guixin.mm.model

import guixin.mm.model.account.{Account, Transfer}
import slick.jdbc.H2Profile.api._

import scala.concurrent.Await
import scala.concurrent.duration._

object DBSchema {

  class AccountTable(tag: Tag) extends Table[Account](tag, "Accounts") {
    def id = column[Int]("ID", O.PrimaryKey, O.AutoInc)

    def userId = column[Int]("USER_ID")

    def balance = column[Double]("BALANCE")

    def currency = column[String]("CURRENCY")

    def * = (id, userId, balance, currency).mapTo[Account]
  }

  val Accounts = TableQuery[AccountTable]

  class TransferTable(tag: Tag) extends Table[Transfer](tag, "Transfers") {
    def id = column[Int]("ID", O.PrimaryKey, O.AutoInc)

    def from = column[Option[Int]]("FROM_ACCOUNT")

    def to = column[Option[Int]]("TO_ACCOUNT")

    def fromAmount = column[Option[Double]]("FROM_AMOUNT")

    def toAmount = column[Option[Double]]("TO_AMOUNT")

    def comment = column[Option[String]]("COMMENT")

    def fromFK = foreignKey("from_FK", from, Accounts)(_.id.?)

    def toFK = foreignKey("to_FK", to, Accounts)(_.id.?)

    def * = (id, from, to, fromAmount, toAmount, comment).mapTo[Transfer]
  }

  val Transfers = TableQuery[TransferTable]

  val databaseSetup = DBIO.seq(
    Accounts.schema.create,
    Transfers.schema.create,

    Accounts.forceInsertAll(Seq(
      Account(1, 1, 100, "usd"),
      Account(2, 1, 500, "jpy"),
      Account(3, 2, 0, "eur"),
      Account(4, 2, 1000, "usd")
    )),

    Transfers.forceInsertAll(Seq(
      Transfer(1, None, Some(1), None, Some(1105), Some("deposit")),
      Transfer(2, Some(1), Some(4), Some(1000), Some(1000), Some("transfer")),
      Transfer(3, Some(1), Some(2), Some(5), Some(500), Some("transfer, rate usd/jpy=100"))
    ))
  )

  val createDatabase: DAO = {
    val db = Database.forConfig("h2mem")

    Await.result(db.run(databaseSetup), 10.seconds)

    new DAO(db)
  }

}
