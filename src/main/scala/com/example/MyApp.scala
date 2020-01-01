package com.example

import java.util.concurrent.{ExecutorService, Executors}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, get, path, _}
import akka.http.scaladsl.server.Route
import com.hazelcast.core.Hazelcast
import akka.util.Timeout
import cats.effect.concurrent.Ref
import cats.effect.{ContextShift, IO}
import cats.syntax.apply._

import scala.concurrent.ExecutionContext.Implicits.global
import com.hazelcast.spi.ExecutionService

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success}

object MyApp extends App {
  val port = args.head.toInt

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


  //#start-http-server
  val ec: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor())
  val hz = Hazelcast.newHazelcastInstance()
  val lockChecker = LockChecker(hz)
  val timer = IO.timer(ec)

  val updateRouteWhenLockAvailable: IO[Unit] = {
    lockChecker.tryGetLock.flatMap { gotLock =>
      IO {
        system.log.info(s"trying to acquire lock. Got it? $gotLock")
        isLocked = gotLock
      } *> timer.sleep(10.seconds) *>  updateRouteWhenLockAvailable
    }
  }

  implicit val system = ActorSystem()


  val lock = hz.getLock("my-app-lock")
  implicit val timeout: Timeout = Timeout(3.seconds)
  implicit val cs: ContextShift[IO] = IO.contextShift(ec)

  val lockCheckerFiber = updateRouteWhenLockAvailable.start.unsafeRunSync()
  val futureBinding = Http().bindAndHandle(routes, "localhost", port)
  futureBinding.onComplete {
    case Success(binding) =>
      val address = binding.localAddress
      system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
    case Failure(ex) =>
      system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
      system.terminate()
  }
}
