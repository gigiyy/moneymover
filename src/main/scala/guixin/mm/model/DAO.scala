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

  def ensureAccount(userId: Int, money: Money): Future[Account] = {
    getAccount(userId, money.currency).map {
      case None => throw new IllegalArgumentException(s"Account not found for user $userId of ${money.currency}")
      case Some(account) => if (account.balance < money.amount)
        throw new IllegalStateException(s"not sufficient balance for user $userId of ${money.currency}")
      else account
    }
  }

  def updateBalance(account: Account, balance: Money): DBIOAction[Int, NoStream, Effect.Write] = {
    require(account.currency == balance.currency)
    require(balance.amount >= 0)
    val q = for (acc <- Accounts if acc.id === account.id) yield acc.balance
    q.update(balance.amount)
  }

  def addTransfer(template: Transfer): DBIOAction[Transfer, NoStream, Effect.Write] = {
    insertAndReturnTransferQuery += template
  }

  def credit(userId: Int, money: Money): Future[String] = {
    getOrCreateAccount(userId, money.currency).map { account =>
      val transfer = Transfer(0, None, Some(account.id), None, Some(money.amount), Some("credit"))
      db.run(DBIO.seq(
        updateBalance(account, account.getBalance + money),
        addTransfer(transfer)
      ).transactionally)
      s"Credited $money successfully to user $userId"
    }
  }

  def debit(userId: Int, money: Money): Future[String] = {
    ensureAccount(userId, money).map { account =>
      val transfer = Transfer(0, Some(account.id), None, Some(money.amount), None, Some("debit"))
      db.run(DBIO.seq(
        updateBalance(account, account.getBalance - money),
        addTransfer(transfer)
      ))
      s"Debited $money successfully from user $userId"
    }
  }

  def transfer(fromUserId: Int, toUserId: Int, money: Money): Future[String] = {
    for {
      from <- ensureAccount(fromUserId, money)
      to <- getOrCreateAccount(toUserId, money.currency)
    } yield {
      val transfer = Transfer(0, Some(from.id), Some(to.id), Some(money.amount), Some(money.amount),
        Some(s"money transfer from $fromUserId to $toUserId"))
      db.run(
        DBIO.seq(
          updateBalance(from, from.getBalance - money),
          updateBalance(to, to.getBalance + money),
          addTransfer(transfer)
        ).transactionally
      )
      s"Transferred $money successfully from user $fromUserId to $toUserId"
    }
  }
}
