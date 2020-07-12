package com.example

import cats.effect.{Blocker, ContextShift, IO, Timer}
import com.hazelcast.core.HazelcastInstance
import io.chrisdavenport.log4cats.Logger
import cats.implicits._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration.FiniteDuration

abstract class SingletonTask {
  def interval: FiniteDuration
  def lockId: String
  def toIO: IO[Unit]
}

object SingletonTask {
  type Ctl = Boolean

  def runEvery(_interval: FiniteDuration, blocker: Blocker, id: String)(logic: IO[Ctl])(implicit hz: HazelcastInstance, cs: ContextShift[IO], timer: Timer[IO], logger: Logger[IO]): SingletonTask = {

    val io: IO[Unit] = for {
        fencedLock <- cs.blockOn(blocker)(IO(hz.getCPSubsystem.getLock(id)))
        continue <- if (fencedLock.tryLock()) logic else Logger[IO].debug(s"couldn't get hold of lock ${id}").as(false)
        _ <- if (continue) timer.sleep(_interval) *> io else Logger[IO].debug(s"task $id completed")
      } yield ()

    new SingletonTask {
      override def interval: FiniteDuration = _interval
      override val lockId = id
      override val toIO = io
    }
  }
}

