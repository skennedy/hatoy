package com.example

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.{complete, get, path, _}
import akka.http.scaladsl.server.Route
import com.example.HALockActor.{AlreadyLocked, GotLock, GetLockResult, TryGetLock}
import com.hazelcast.core.Hazelcast
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success}

object MyApp extends App {
  val waitingRoute: Route =
    get {
      path("health") {
        complete("waiting for lock ...")
      }
    }

  val lockedRoute: Route =
    get {
      path("health") {
        complete("got the lock :)")
      }
    }

  val port = args.head.toInt


  @volatile var routes: Route = waitingRoute

  //#start-http-server
  val hz = Hazelcast.newHazelcastInstance()
  val HALockSystem = ActorSystem[TryGetLock](HALockActor(hz), "HA-Actor-system")


  implicit val system = HALockSystem.toClassic

  val lock = hz.getLock("my-app-lock")
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
  implicit val timeout: Timeout = Timeout(3.seconds)
  implicit val scheduler = schedulerFromActorSystem(HALockSystem)

  val getLockResult = Await.result(HALockSystem.ask[GetLockResult](ref => HALockActor.TryGetLock(ref)), 15.seconds)

  routes = getLockResult match {
    case AlreadyLocked => waitingRoute
    case GotLock => lockedRoute
  }

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
