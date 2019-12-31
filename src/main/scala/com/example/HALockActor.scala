package com.example


import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.hazelcast.core.HazelcastInstance
import scala.concurrent.duration._

object HALockActor {
  val RetryInterval = 1.minutes
  final case class TryGetLock(replyTo: ActorRef[GetLockResult])

  sealed trait GetLockResult extends Product with Serializable
  final case object AlreadyLocked extends GetLockResult
  final case object GotLock extends GetLockResult

  def apply(hz: HazelcastInstance): Behavior[TryGetLock] = Behaviors.receive { (context, message) =>
    val lock = hz.getLock("my-app-lock")
    val reply = if (lock.tryLock())
      GotLock
      else
      AlreadyLocked
    context.log.info("replying to TryGetLock request: {}", reply)
    message.replyTo ! reply
    context.scheduleOnce(RetryInterval, context.self, TryGetLock(context.self))
    Behaviors.same
  }

  def watcher(lockActor: ActorRef[TryGetLock]): Behavior[GetLockResult] = ???
}

