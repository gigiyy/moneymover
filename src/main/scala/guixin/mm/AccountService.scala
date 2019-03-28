package guixin.mm

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.pipe
import guixin.mm.AccountService._
import guixin.mm.Worker.Done
import guixin.mm.model.DAO
import guixin.mm.model.account.Money

import scala.concurrent.{ExecutionContextExecutor, Future}

class Worker(replyTo: ActorRef, message: Transfer, db: DAO) extends Actor {

  implicit val ec: ExecutionContextExecutor = context.dispatcher
  val future: Future[String] = message match {
    case Credit(userId, amount, currency) =>
      db.credit(userId, Money(amount, currency))
    case Debit(userId, amount, currency) =>
      db.debit(userId, Money(amount, currency))
    case Trans(debit, credit) =>
      db.transfer(debit.userId, credit.userId, Money(debit.amount, debit.currency),
        Money(credit.amount, credit.currency))
  }

  future.map(Ok).recover {
    case e: IllegalArgumentException => Err(e.getMessage)
    case e: IllegalStateException => Err(e.getMessage)
    case e => SysErr(e.getMessage)
  } pipeTo context.self

  def receive: Receive = {
    case msg: Result =>
      replyTo ! msg
      context.parent ! Done(message.ids)
  }
}

object Worker {

  case class Done(ids: Set[Int])

  def props(replyTo: ActorRef, message: Transfer, db: DAO) = Props(new Worker(replyTo, message, db))
}

class AccountService(val db: DAO) extends Actor {

  def receive: Receive = normal(Set.empty, Vector.empty)

  implicit val ec: ExecutionContextExecutor = context.dispatcher

  /**
    * service main loop.
    * <p>
    * to avoid race conditions where multiple updates to same group of user's accounts happens simultaneously,
    * `AccountService` will cache the users' ID in `working` set until their processes were done.
    * <p/>
    * pending messages and their sender were cached in `waiting` queue
    * until `Done` message received. pending message will be retried one by one.
    * <p>
    * <b>in reality, working set should be backed by shared MemCache so that we can have multiple instance
    * of `AccountService` or `Server` itself.</b>
    *
    * @param working cache user IDs currently under processing
    * @param waiting Transfer messages pending processing
    * @return
    */
  def normal(working: Set[Int], waiting: Vector[(ActorRef, Transfer)]): Receive = {
    case msg: Transfer =>
      if (working.intersect(msg.ids).nonEmpty) context.become(normal(working, waiting :+ (sender(), msg)))
      else {
        context.actorOf(Worker.props(sender(), msg, db))
        context.become(normal(working ++ msg.ids, waiting))
      }
    case Done(ids) =>
      context.stop(sender())
      var next = working -- ids
      var pending: Vector[(ActorRef, Transfer)] = Vector.empty
      waiting.foreach {
        case (replyTo, msg) =>
          val cur = msg.ids
          if (next.intersect(cur).isEmpty) {
            next ++= cur
            context.actorOf(Worker.props(replyTo, msg, db))
          }
          else pending :+= ((replyTo, msg))
      }
      context.become(normal(next, pending))
    case WorkingIds => sender() ! working
    case User(userId) =>
      db.getUserInfo(userId).map {
        case seq if seq.nonEmpty =>
          val accounts = seq.map {
            case (account, records) =>
              val transactions = records.map {
                case model.account.Credit(id, _, amount, fromAccount, _) =>
                  val action = if (fromAccount.nonEmpty) Received else Deposit
                  Transaction(id, amount, action.toString)
                case model.account.Debit(id, _, amount, toAccount, _) =>
                  val action = if (toAccount.nonEmpty) Sent else Withdraw
                  Transaction(id, amount, action.toString)
              }
              Account(account.id, Money(account.balance, account.currency), transactions)
          }
          UserInfo(userId, accounts)
        case Nil =>
          Err(s"User $userId no found.")
      }.recover {
        case e =>
          println(e)
          SysErr("server error")
      } pipeTo sender()
  }
}

object AccountService {

  sealed trait Query

  final case object WorkingIds extends Query

  final case class User(userId: Int) extends Query

  sealed trait Transfer {
    def ids: Set[Int] = this match {
      case Credit(id, _, _) => Set(id)
      case Debit(id, _, _) => Set(id)
      case Trans(d, c) => Set(d.userId, c.userId)
    }
  }

  final case class Credit(userId: Int, amount: Double, currency: String) extends Transfer

  final case class Debit(userId: Int, amount: Double, currency: String) extends Transfer

  final case class Trans(debit: Debit, credit: Credit) extends Transfer

  sealed trait Result

  final case class Ok(message: String) extends Result

  final case class Err(message: String) extends Result

  final case class SysErr(message: String) extends Result

  final case class UserInfo(id: Int, accounts: Seq[Account]) extends Result

  case class Account(id: Int, balance: Money, records: Seq[Transaction])

  case class Transaction(id: Int, money: Double, action: String)

  sealed trait Action

  final case object Deposit extends Action

  final case object Withdraw extends Action

  final case object Sent extends Action

  final case object Received extends Action

  def props(db: DAO) = Props(new AccountService(db))
}
