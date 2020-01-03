package com.example

import java.util.concurrent.Executors

import cats.effect.{Fiber, IO, Resource}
import cats.syntax.apply._
import com.hazelcast.config.Config
import com.hazelcast.config.cp.CPSubsystemConfig
import com.hazelcast.core.{Hazelcast, HazelcastInstance}
import com.hazelcast.cp.CPSubsystem
import com.hazelcast.cp.lock.FencedLock

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

trait LockChecker {
  def observeLockStatus(pollInterval: FiniteDuration)(observe: Boolean => IO[Unit]): IO[Fiber[IO, Unit]]
}
object LockChecker {
  val LockName = "HA-lock"
  private def apply(hzCP: CPSubsystem, singleThreadEc: ExecutionContext): LockChecker =  new LockChecker {
    val tryGetLock = for {
      lock <- IO(hzCP.getLock(LockName))
      gotLock <- IO(lock.tryLock())
    } yield gotLock

    val timer = IO.timer(singleThreadEc)
    implicit val cs = IO.contextShift(singleThreadEc)

    def pollLockStatus(interval: FiniteDuration)(observe: Boolean => IO[Unit]): IO[Unit] = tryGetLock.flatMap { lockStatus =>
      observe(lockStatus) *> timer.sleep(interval) *> pollLockStatus(interval)(observe)
    }

    override def observeLockStatus(interval: FiniteDuration)(observe: Boolean => IO[Unit]): IO[Fiber[IO, Unit]] = pollLockStatus(interval)(observe).start

  }

  private val hazelcastInstance: Resource[IO, HazelcastInstance] = {
    val config = new Config()
    val cpSubsystemConfig = new CPSubsystemConfig()
    cpSubsystemConfig.setCPMemberCount(3)
    cpSubsystemConfig.setGroupSize(3)
    config.setCPSubsystemConfig(cpSubsystemConfig)
    Resource.make(IO(Hazelcast.newHazelcastInstance(config)))(hz => IO(hz.shutdown()))
  }

  private val singleThreadEC: Resource[IO, ExecutionContextExecutorService] =
    Resource.make(IO(ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor())))(ec => IO(ec.shutdown()))
    Resource.make(IO(Hazelcast.newHazelcastInstance()))(hz => IO(hz.shutdown()))

  val resource: Resource[IO, LockChecker] = for {
    hz <- hazelcastInstance
    ec <- singleThreadEC
  } yield LockChecker(hz.getCPSubsystem, ec)
}
