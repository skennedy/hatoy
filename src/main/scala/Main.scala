import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.example.imperative._
import org.slf4j.LoggerFactory
import org.apache.ignite.Ignition
import org.apache.ignite.cache.CacheAtomicityMode
import org.apache.ignite.configuration.IgniteConfiguration
import org.apache.ignite.events.EventType

object Main extends App {
  val port: Int =
    args.headOption.map(_.toInt).getOrElse {
      throw new IllegalArgumentException("port expected")
    }

  val logger = LoggerFactory.getLogger("main")

//  class NodeLifecycleListener extends LifecycleListener {
//    override def stateChanged(event: LifecycleEvent) {
//      logger.debug(s"intercepting lifecycle event ${event}")
//    }
//  }
//  val config            = new Config()
//  val cpSubsystemConfig = new CPSubsystemConfig()
//  //setting this value to 0 disables the CP subsystem as we want to run even with less than 3 instances
//  cpSubsystemConfig.setCPMemberCount(0)
//  cpSubsystemConfig.setSessionHeartbeatIntervalSeconds(1)
//  cpSubsystemConfig.setSessionTimeToLiveSeconds(5)
//  config.setCPSubsystemConfig(cpSubsystemConfig)
//
//  val hz = Hazelcast.newHazelcastInstance(config)
//  hz.getLifecycleService.addLifecycleListener(new NodeLifecycleListener)

  val cfg = new IgniteConfiguration
  cfg.setIncludeEventTypes(EventType.EVTS_CACHE: _*)

  val ignite = Ignition.start(cfg)

  val msg = ignite.message()

//  import org.apache.ignite.configuration.CacheConfiguration
//
//  val currentWeatherConfig = new CacheConfiguration[String, CurrentWeather]
//
//  currentWeatherConfig.setName("myCache")
//  currentWeatherConfig.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL)

  implicit val actorSystem = ActorSystem()

  logger.info("Booting")

  import akka.http.scaladsl.server.Directives._
  val routes: Route =
    get {
      path("current-weather" / Segment) { city =>
        pathEndOrSingleSlash {
          Option(ignite.getOrCreateCache[String, CurrentWeather](Maps.currentWeather).get(city))
            .fold(complete(StatusCodes.NotFound))(conditions => complete(conditions.toString))
        }
      } ~
        path("ads") {
          pathEndOrSingleSlash {
            handleWebSocketMessages(AdvertsFlow(msg))
          }
        }
    }

  Http().bindAndHandle(routes, "0.0.0.0", port)
  SingletonTask.currentWeather(ignite)
}
