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

  future pipeTo context.self

  def receive: Receive = {
    case msg: String =>
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

  /**
    * service main loop
    *
    * to avoid race conditions where multiple updates to same group of user's accounts happens simultaneously,
    * `AccountService` will cache the users' ID in `working` set until their processes were done
    * pending messages and their sender were cached in `waiting` queue
    * after `Done` message received, pending message will be retried one by one
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
  }
}

object AccountService {


  sealed trait Query

  final case object WorkingIds extends Query

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

  def props(db: DAO) = Props(new AccountService(db))
}
