package com.example

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, get, path, _}
import akka.http.scaladsl.server.Route
import cats.effect.{ExitCode, IO, IOApp, Resource, Sync}
import cats.syntax.apply._
import cats.syntax.either._
import cats.syntax.functor._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object MyApp extends IOApp {

  implicit def unsafeLogger[F[_]: Sync] = Slf4jLogger.getLogger[F]
  val actorSystemResource: Resource[IO, ActorSystem] = Resource.make(IO(ActorSystem()))(as => IO.fromFuture(IO(as.terminate())).void)

  def boot(port: Int): IO[ExitCode] = {
    @volatile var isLocked: Boolean = false
    val routes: Route =
      get {
        pathSingleSlash {
          complete("hello")
        } ~ path("health") {
          complete {
            if (isLocked)
              s"Ok, lock held by service running on port $port"
            else StatusCodes.Locked
          }
        }
      }

    def bindService(implicit actorSystem: ActorSystem) = IO.fromFuture {
      IO(Http().bindAndHandle(routes, "localhost", port))
    }.flatMap(binding => Logger[IO].info(s"Server online at http://${binding.localAddress.getHostString}:${binding.localAddress.getPort}")) *> IO.never

    (for {
      actorSystem <- actorSystemResource
      lockChecker <- LockChecker.resource
    } yield lockChecker -> actorSystem).use { case (lockChecker, actorSystem) =>

      val updateLockState = { gotLock: Boolean =>
        IO {
          isLocked = gotLock
        } *> Logger[IO].info(s"Polling lock status... got lock? $gotLock")
      }

      (lockChecker.observeLockStatus(10.seconds)(updateLockState) *> bindService(actorSystem)).as(ExitCode.Success)
    }
  }


  override def run(args: List[String]): IO[ExitCode] =
    args match {
      case port :: _   =>
        val invalidArgumentErr = Logger[IO].error(s"Couldn't parse argument ${port} as a port number").as(ExitCode.Error)
        Either.catchNonFatal(port.toInt).fold(_ => invalidArgumentErr, boot)
      case _ =>
        IO {
          System.err.println("usage: MyApp [PORT] ")
          ExitCode.Error
        }
    }

}
