package com.example

import java.util.concurrent.Executors
import java.util.concurrent.locks.Lock

import cats.effect.{Fiber, IO, Resource, Timer}
import cats.syntax.apply._
import com.hazelcast.config.Config
import com.hazelcast.config.cp.CPSubsystemConfig
import com.hazelcast.core.{DistributedObject, Hazelcast, HazelcastInstance, LifecycleEvent, LifecycleListener}
import com.hazelcast.cp.lock.FencedLock
import io.chrisdavenport.log4cats.Logger
import cats.syntax.apply._

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
  private def apply(lock: FencedLock, singleThreadEc: ExecutionContext)(implicit logger: Logger[IO]): LockChecker =  new LockChecker {
    override val timer = IO.timer(singleThreadEc)
    override val tryGetLock = for {
      gotLock <- IO.shift(singleThreadEc) *> IO(lock.tryLock())
    } yield gotLock

  }

  private def hazelcastInstance(implicit logger: Logger[IO]): Resource[IO, HazelcastInstance] = {
    class NodeLifecycleListener extends LifecycleListener {
      override def stateChanged(event: LifecycleEvent) {
        logger.info(s"intercepting lifecycle event ${event}").unsafeRunSync
      }
    }
    val config = new Config()
    val cpSubsystemConfig = new CPSubsystemConfig()
    cpSubsystemConfig.setCPMemberCount(3)
    cpSubsystemConfig.setGroupSize(3)
//    cpSubsystemConfig.setSessionHeartbeatIntervalSeconds(1)
//    cpSubsystemConfig.setSessionTimeToLiveSeconds(5)
    config.setCPSubsystemConfig(cpSubsystemConfig)
    Resource.make {
      IO {
        val hz = Hazelcast.newHazelcastInstance(config)
        hz.getLifecycleService.addLifecycleListener(new NodeLifecycleListener)
        hz
      }
    }(hz => IO(hz.shutdown()))
  }

  private val singleThreadEC: Resource[IO, ExecutionContextExecutorService] =
    Resource.make(IO(ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor())))(ec => IO(ec.shutdown()))
    Resource.make(IO(Hazelcast.newHazelcastInstance()))(hz => IO(hz.shutdown()))

  private def lockResource(hz: HazelcastInstance, ec: ExecutionContext)(implicit logger: Logger[IO]): Resource[IO, FencedLock] =
    Resource.make(IO.shift(ec) *> IO(hz.getCPSubsystem.getLock(LockName))) { lock =>
      logger.info("trying to unlock lock") *> IO.shift(ec) *> IO {
        if (lock.isLockedByCurrentThread)
          lock.unlock()
        else
          logger.info("not unlocking down lock")
      }

    }

  def resource(implicit logger: Logger[IO]): Resource[IO, LockChecker] = for {
    hz <- hazelcastInstance
    ec <- singleThreadEC
    lock <- lockResource(hz, ec)
  } yield LockChecker(lock, ec)
}
