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
    getOrCreateAccount(userId, money.currency).flatMap { account =>
      val transfer = Transfer(0, None, Some(account.id), None, Some(money.amount), Some("credit"))
      db.run(
        DBIO.seq(
          updateBalance(account, account.getBalance + money),
          addTransfer(transfer)
        ).transactionally
      ).map { _ =>
        s"Credited $money successfully to user $userId"
      }
    }
  }

  def debit(userId: Int, money: Money): Future[String] = {
    ensureAccount(userId, money).flatMap { account =>
      val transfer = Transfer(0, Some(account.id), None, Some(money.amount), None, Some("debit"))
      db.run(
        DBIO.seq(
          updateBalance(account, account.getBalance - money),
          addTransfer(transfer)
        ).transactionally
      ).map { _ =>
        s"Debited $money successfully from user $userId"
      }
    }
  }

  def transfer(fromUserId: Int, toUserId: Int, debit: Money, credit: Money): Future[String] = {
    require(debit.amount > 0 && credit.amount > 0)
    require(fromUserId != toUserId || (fromUserId == toUserId && debit.currency == credit.currency))
    val fromAccount = ensureAccount(fromUserId, debit)
    val toAccount = getOrCreateAccount(toUserId, credit.currency)
    fromAccount.flatMap { from =>
      toAccount.flatMap { to =>
        val transfer = Transfer(0, Some(from.id), Some(to.id), Some(debit.amount), Some(credit.amount),
          Some(s"money transfer from $fromUserId to $toUserId"))
        db.run(
          DBIO.seq(
            updateBalance(from, from.getBalance - debit),
            updateBalance(to, to.getBalance + credit),
            addTransfer(transfer)
          ).transactionally
        ).map { _ =>
          if (debit.currency == credit.currency)
            s"Transferred $debit from user $fromUserId to user $toUserId"
          else if (fromUserId == toUserId)
            s"Converted from $debit to $credit for user $fromUserId, rate ${debit.currency}/${credit.currency}=${credit.amount / debit.amount}"
          else
            s"Transferred $debit from user $fromUserId to user $toUserId ($credit)"
        }
      }
    }
  }

  def transfer(fromUserId: Int, toUserId: Int, money: Money): Future[String] = {
    require(fromUserId != toUserId)
    require(money.amount > 0)
    transfer(fromUserId, toUserId, money, money)
  }

  def transfer(userId: Int, debit: Money, credit: Money): Future[String] = {
    require(debit.currency != credit.currency)
    require(debit.amount > 0 && credit.amount > 0)
    transfer(userId, userId, debit, credit)
  }
}
