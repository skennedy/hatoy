package com.example

import java.util.concurrent.{Executors, TimeUnit}

import cats.effect.{IO, Resource, Timer}
import cats.syntax.apply._
import com.hazelcast.config.Config
import com.hazelcast.config.cp.CPSubsystemConfig
import com.hazelcast.core.{Hazelcast, HazelcastInstance, LifecycleEvent, LifecycleListener}
import io.chrisdavenport.log4cats.Logger

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

trait LockChecker {
  def timer: Timer[IO]
  def tryGetLock: IO[Boolean]
  def pollLockStatus(interval: FiniteDuration)(observe: Boolean => IO[Unit]): IO[Unit] = {
    tryGetLock.flatMap { lockStatus =>
      observe(lockStatus) *> timer.sleep(interval) *> pollLockStatus(interval)(observe)
    }
  }

}
object LockChecker {
  val LockName = "HA-lock"
  private def apply(hz: HazelcastInstance, singleThreadEc: ExecutionContext)(implicit logger: Logger[IO]): LockChecker =  new LockChecker {
    override val timer = IO.timer(singleThreadEc)
    override val tryGetLock = for {
      lock <- IO.shift(singleThreadEc) *> IO(hz.getCPSubsystem.getLock(LockName))
      gotLock <- IO.shift(singleThreadEc) *> IO(lock.tryLock())
    } yield gotLock
  }


  private val singleThreadEC: Resource[IO, ExecutionContextExecutorService] =
    Resource.make(IO(ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor())))(ec => IO(ec.shutdown()))
    Resource.make(IO(Hazelcast.newHazelcastInstance()))(hz => IO(hz.shutdown()))

//  private def lockResource(hz: HazelcastInstance, ec: ExecutionContext)(implicit logger: Logger[IO]): Resource[IO, FencedLock] =
//    Resource.make(IO.shift(ec) *> IO(hz.getCPSubsystem.getLock(LockName))) { lock =>
//      logger.info("trying to unlock lock") *> IO.shift(ec) *> IO {
//        if (lock.isLockedByCurrentThread)
//          lock.unlock()
//        else
//          logger.info("not unlocking lock")
//      }
//
//    }

  def resource(implicit hz: HazelcastInstance, logger: Logger[IO]): Resource[IO, LockChecker] = for {
    ec <- singleThreadEC
  } yield LockChecker(hz, ec)
}
