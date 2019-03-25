package guixin.mm.model

import guixin.mm.model.account.{Account, Record, Transfer}
import slick.jdbc.H2Profile.api._
import guixin.mm.model.DBSchema._

import scala.concurrent.{ExecutionContext, Future}

class DAO(val db: Database) {

  implicit val executionContext: ExecutionContext = ExecutionContext.global

  def allAccounts: Future[Seq[Account]] = db.run(Accounts.result)

  def getAccount(id: Int): Future[Option[Account]] = db.run(
    Accounts.filter(_.id === id).result.headOption
  )

  def accountRecords(id: Int): Future[Seq[Record]] = db.run(
    Transfers.filter(t => t.from === id || t.to === id).result
      .map(_.flatMap(_.getRecords.filter(_.accountId == id)))
  )

}
