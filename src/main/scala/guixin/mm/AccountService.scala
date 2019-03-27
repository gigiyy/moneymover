package guixin.mm

import akka.NotUsed
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior, Terminated}
import guixin.mm.model.DAO
import guixin.mm.model.account.Money

import scala.concurrent.{ExecutionContextExecutor, Future}

object AccountService {

  sealed trait Command

  final case class Done(ids: Set[Int]) extends Command

  sealed trait Transfer extends Command {
    def ids: Set[Int] = this match {
      case Credit(id, _, _) => Set(id)
      case Debit(id, _, _) => Set(id)
      case Trans(debit, credit) => Set(debit.userId, credit.userId)
    }
  }

  final case class Credit(userId: Int, amount: Double, currency: String) extends Transfer

  final case class Debit(userId: Int, amount: Double, currency: String) extends Transfer

  final case class Trans(debit: Debit, credit: Credit) extends Transfer

  /**
    * create the behavior that will receive money transfer orders
    * to avoid conflicts, user id involved in current transactions
    * will be keep in `working` set, and subsequent messages will be buffered in `waiting` `StashBuffer`
    *
    * in reality, the `working` set should be backed by shared mem-cache,
    * so that our service can be scaled out.
    *
    * @param bufferSize maximum size of the waiting buffer
    * @return
    */
  def apply(bufferSize: Int, db: DAO, replyTo: ActorRef[Future[String]]): Behavior[Transfer] =
    normal(Set.empty, StashBuffer(bufferSize), bufferSize, db, replyTo).narrow

  def normal(working: Set[Int], waiting: StashBuffer[Command], buffSize: Int, db: DAO, replyTo: ActorRef[Future[String]]): Behavior[Command] =
    Behaviors.receive[Command] {
      case (ctx, msg: Transfer) =>
        if ((working & msg.ids).nonEmpty) normal(working, waiting.stash(msg), buffSize, db, replyTo)
        else {
          val child = ctx.spawnAnonymous(transfer(msg, ctx.self, db, replyTo))
          ctx.watch(child)
          normal(working ++ msg.ids, waiting, buffSize, db, replyTo)
        }
      case (ctx, Done(ids)) =>
        val next = working -- ids
        waiting.unstashAll(ctx, normal(next, StashBuffer(buffSize), buffSize, db, replyTo))
    }.receiveSignal {
      case (_, Terminated(_)) =>
        // need to check what to do with these really!
        Behavior.same
    }

  def transfer(transfer: Transfer, complete: ActorRef[Done], db: DAO, replyTo: ActorRef[Future[String]]): Behavior[NotUsed] =
    Behaviors.setup[AnyRef] { ctx =>
      implicit val ec: ExecutionContextExecutor = ctx.executionContext

      val result = transfer match {
        case Credit(userId, amount, currency) =>
          db.credit(userId, Money(amount, currency))
        case Debit(userId, amount, currency) =>
          db.debit(userId, Money(amount, currency))
        case Trans(debit, credit) =>
          db.transfer(debit.userId, credit.userId,
            Money(debit.amount, debit.currency),
            Money(credit.amount, credit.currency))
      }
      val ids = transfer.ids
      replyTo ! result
      result.map(_ => complete ! Done(ids)).recover {
        case _: IllegalArgumentException => complete ! Done(ids)
        case _: IllegalStateException => complete ! Done(ids)
        // other failures should terminate the actor
        // so we should deal with it in normal Behavior
      }
      Behaviors.empty
    }.narrow[NotUsed]
}
