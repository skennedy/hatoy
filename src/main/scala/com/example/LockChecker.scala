package com.example

import cats.effect.IO
import com.hazelcast.core.HazelcastInstance

trait LockChecker {
  def tryGetLock: IO[Boolean]
}
object LockChecker {
  def apply(hz: HazelcastInstance): LockChecker = new LockChecker {
    override def tryGetLock: IO[Boolean] = for {
      lock <- IO(hz.getLock("ha-lock"))
      locked <- IO(lock.tryLock())
    } yield locked
  }
}
