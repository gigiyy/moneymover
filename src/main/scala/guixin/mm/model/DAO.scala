package guixin.mm.model

import guixin.mm.model.account.{Account, Money, Record, Transfer}
import slick.jdbc.H2Profile.api._
import guixin.mm.model.DBSchema._

import scala.concurrent.{ExecutionContext, Future}

class DAO(val db: Database) {

  implicit val executionContext: ExecutionContext = ExecutionContext.global

  def allAccounts: Future[Seq[Account]] = db.run(Accounts.result)

  def getAccount(accountId: Int): Future[Option[Account]] = db.run(
    Accounts.filter(_.id === accountId).result.headOption
  )

  def getAccount(userId: Int, currency: String): Future[Option[Account]] =
    db.run(
      Accounts.filter(acc => acc.userId === userId && acc.currency === currency).result.headOption
    )

  def accountRecords(accountId: Int): Future[Seq[Record]] = db.run(
    Transfers.filter(t => t.from === accountId || t.to === accountId).result
      .map(_.flatMap(_.getRecords.filter(_.accountId == accountId)))
  )

  def addAccount(userId: Int, currency: String): Future[Account] = {
    val newAccount = Account(0, userId, 0, currency)

    val insertAndReturnAccountQuery = (Accounts returning Accounts.map(_.id)) into {
      (account, id) => account.copy(id = id)
    }

    db.run(insertAndReturnAccountQuery += newAccount)
  }

  def getOrCreateAccount(userId: Int, currency: String): Future[Account] =
    getAccount(userId, currency).flatMap {
      case Some(account) => Future.successful(account)
      case None => addAccount(userId, currency)
    }

  val insertAndReturnTransferQuery = (Transfers returning Transfers.map(_.id)) into {
    (transfer, id) => transfer.copy(id = id)
  }

  def creditActions(userId: Int, money: Money): Future[DBIOAction[Unit, NoStream, Effect.Write]] = {
    getOrCreateAccount(userId, money.currency).map { account =>
      val next = account.getBalance + money
      val transfer = Transfer(0, None, Some(account.id), None, Some(money.amount), Some("credit"))

      val q = for {a <- Accounts if a.id === account.id} yield a.balance
      val updateBalance = q.update(next.amount)

      DBIO.seq(
        updateBalance,
        insertAndReturnTransferQuery += transfer
      )
    }
  }

  def debitActions(userId: Int, money: Money): Future[DBIOAction[Unit, NoStream, Effect.Write]] = {
    getAccount(userId, money.currency).map {
      case None => throw new IllegalArgumentException(s"Account not found for user $userId of ${money.currency}")
      case Some(account) =>
        if (account.balance < money.amount)
          throw new IllegalStateException(s"not sufficient balance for user $userId of ${money.currency}")
        else {
          val next = account.getBalance - money
          val transfer = Transfer(0, Some(account.id), None, Some(money.amount), None, Some("debit"))

          val q = for {a <- Accounts if a.id === account.id} yield a.balance
          val updateBalance = q.update(next.amount)

          DBIO.seq(
            updateBalance,
            insertAndReturnTransferQuery += transfer
          )
        }
    }
  }

  def credit(userId: Int, money: Money): Future[String] = {
    creditActions(userId, money).map { actions =>
      db.run(actions.transactionally)
      s"Credited $money successfully to user $userId"
    }
  }

  def debit(userId: Int, money: Money): Future[String] = {
    debitActions(userId, money).map { actions =>
      db.run(actions.transactionally)
      s"Debited $money successfully from user $userId"
    }
  }

  def transfer(fromUserId: Int, toUserId: Int, money: Money): Future[String] = {
    val transferActions = for {
      debit <- debitActions(fromUserId, money)
      credit <- creditActions(toUserId, money)
    } yield debit.andThen(credit)

    transferActions.map { actions =>
      db.run(actions.transactionally)
      s"Transferred $money successfully from user $fromUserId to $toUserId"
    }
  }


}
