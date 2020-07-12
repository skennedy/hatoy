package com.example

import cats.effect.concurrent.Ref
import cats.effect.{ExitCode, IO, IOApp, Resource}

object KillIO extends IOApp {
  val resource: Resource[IO, Ref[IO, Int]] = Resource.make(Ref.of[IO, Int](0))(_.get.flatMap(n => IO(println(s"Shutting down counter at value $n"))))

  override def run(args: List[String]): IO[ExitCode] =
    resource.use(ref => ref.update(n => n + 1).flatMap(_ => IO(Thread.sleep(30000))).map(_ => ExitCode.Success))
}
