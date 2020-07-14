package com.example

import cats.effect.Blocker
import cats.effect.ContextShift
import cats.effect.Fiber
import cats.effect.IO
import cats.effect.Timer
import com.hazelcast.core.HazelcastInstance
import io.chrisdavenport.log4cats.Logger

import scala.concurrent.duration.FiniteDuration

abstract class SingletonTask {
  def interval: FiniteDuration
  def lockId:   String
  def spawn:    IO[Fiber[IO, Unit]]
}

object SingletonTask {
  type Ctl = Boolean

  def runEvery(
    _interval: FiniteDuration,
    blocker:   Blocker,
    id:        String,
  )(logic:     IO[Ctl],
  )(
    implicit
    hz:     HazelcastInstance,
    cs:     ContextShift[IO],
    timer:  Timer[IO],
    logger: Logger[IO],
  ): SingletonTask = {

    def io: IO[Unit] = for {
      isLocked <- cs.blockOn(blocker)(IO(hz.getCPSubsystem.getLock(id).tryLock()))
      continue <- if (isLocked) logic else Logger[IO].debug(s"couldn't get hold of lock ${id}").as(false)
      _        <- if (continue) timer.sleep(_interval) *> io else Logger[IO].debug(s"task $id completed")
    } yield ()

    new SingletonTask {
      override def interval: FiniteDuration = _interval
      override val lockId = id
      override val spawn  = io.start
    }
  }
}
