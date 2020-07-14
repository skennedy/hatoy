package com.example

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.path
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.effect.Sync
import cats.syntax.apply._
import cats.syntax.either._
import cats.syntax.functor._
import com.hazelcast.config.Config
import com.hazelcast.config.cp.CPSubsystemConfig
import com.hazelcast.core.Hazelcast
import com.hazelcast.core.HazelcastInstance
import com.hazelcast.core.LifecycleEvent
import com.hazelcast.core.LifecycleListener
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object MyApp extends IOApp {

  implicit def unsafeLogger[F[_]: Sync] = Slf4jLogger.getLogger[F]
  val actorSystemResource: Resource[IO, ActorSystem] =
    Resource.make(IO(ActorSystem()))(as => IO.fromFuture(IO(as.terminate())).void)

  private def hazelcastInstance(implicit logger: Logger[IO]): Resource[IO, HazelcastInstance] = {
    class NodeLifecycleListener extends LifecycleListener {
      override def stateChanged(event: LifecycleEvent) {
        logger.info(s"intercepting lifecycle event ${event}").unsafeRunSync
      }
    }
    val config            = new Config()
    val cpSubsystemConfig = new CPSubsystemConfig()
    cpSubsystemConfig.setCPMemberCount(0)
//    cpSubsystemConfig.setGroupSize(3)
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

  def boot(port: Int): IO[ExitCode] = {
    @volatile var isLocked: Boolean = false
    val routes: Route = get {
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

    def bindService(implicit actorSystem: ActorSystem): IO[Unit] =
      IO.fromFuture {
        IO(Http().bindAndHandle(routes, "localhost", port))
      }.flatMap(bindings => Logger[IO].info(s"Server bound on port ${bindings.localAddress.getPort}"))

    (for {
      actorSystem <- actorSystemResource
      hz          <- hazelcastInstance
      mapMaker     = new MapMaker(hz)
      lockChecker <- LockChecker.resource(hz, Logger[IO])
    } yield (lockChecker, actorSystem, mapMaker)).use {
      case (lockChecker, actorSystem, mapMaker) =>
        import MapMaker._

        val uuid = UUID.fromString("25a62704-6f98-4a23-8f21-ed1153e0aee6")
        println(s"getting a distributed map value: ${mapMaker.get(Key("a"))}")
        val a = mapMaker.get(Key("a"))
        println(s"checking structural equality works for $a: ${a == Some(Value(uuid))}")
        mapMaker.set(Key("a"), Value(uuid))

        val a1 = mapMaker.get(Key("a"))
        println(s"Got a1 right after set: $a1, waiting for 1000 millis")
        Thread.sleep(1010)

        val a2 = mapMaker.get(Key("a"))
        println(s"Got a2: $a2")

        val updateLockState = { gotLock: Boolean =>
          IO {
            isLocked = gotLock
          } *> Logger[IO].info(s"Polling lock status... got lock? $gotLock")
        }

        (lockChecker.pollLockStatus(10.seconds)(updateLockState).start *> bindService(actorSystem) *> IO.never).as(
          ExitCode.Success,
        )
    }
  }

  override def run(args: List[String]): IO[ExitCode] =
    args match {
      case port :: _ =>
        val invalidArgumentErr =
          Logger[IO].error(s"Couldn't parse argument ${port} as a port number").as(ExitCode.Error)
        Either.catchNonFatal(port.toInt).fold(_ => invalidArgumentErr, boot)
      case _ =>
        IO {
          System.err.println("usage: MyApp [PORT] ")
          ExitCode.Error
        }
    }

}
