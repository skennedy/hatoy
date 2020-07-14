package com.example.imperative

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.hazelcast.config.Config
import com.hazelcast.config.cp.CPSubsystemConfig
import com.hazelcast.core.Hazelcast
import com.hazelcast.core.LifecycleEvent
import com.hazelcast.core.LifecycleListener
import org.slf4j.LoggerFactory

object Main extends App {
  val port: Int =
    args.headOption.map(_.toInt).getOrElse {
      throw new IllegalArgumentException("port expected")
    }

  val logger = LoggerFactory.getLogger("main")

  class NodeLifecycleListener extends LifecycleListener {
    override def stateChanged(event: LifecycleEvent) {
      logger.info(s"intercepting lifecycle event ${event}")
    }
  }
  val config            = new Config()
  val cpSubsystemConfig = new CPSubsystemConfig()
  cpSubsystemConfig.setCPMemberCount(0)
  //    cpSubsystemConfig.setGroupSize(3)
  cpSubsystemConfig.setSessionHeartbeatIntervalSeconds(1)
  cpSubsystemConfig.setSessionTimeToLiveSeconds(5)

  config.setCPSubsystemConfig(cpSubsystemConfig)
  val hz = Hazelcast.newHazelcastInstance(config)
  hz.getLifecycleService.addLifecycleListener(new NodeLifecycleListener)

  val adsTopic = hz.getTopic[Advert](Topic.ads)

  implicit val actorSystem = ActorSystem()

  val routes: Route =
    get {
      path("current-weather" / Segment) { city =>
        pathEndOrSingleSlash {
          Option(hz.getMap[String, CurrentWeather](Maps.currentWeather).get(city))
            .fold(complete(StatusCodes.NotFound))(conditions => complete(conditions.toString))
        }
      } ~
        path("ads") {
          pathEndOrSingleSlash {
            handleWebSocketMessages(AdvertsFlow(adsTopic))
          }
        }
    }

  Http().bindAndHandle(routes, "0.0.0.0", port)
  SingletonTask0.currentWeather(hz)
}
