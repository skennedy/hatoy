import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import com.example.imperative._
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
      logger.debug(s"intercepting lifecycle event ${event}")
    }
  }
  val config            = new Config()
  val cpSubsystemConfig = new CPSubsystemConfig()
  //setting this value to 0 disables the CP subsystem as we want to run even with less than 3 instances
  cpSubsystemConfig.setCPMemberCount(0)
  cpSubsystemConfig.setSessionHeartbeatIntervalSeconds(1)
  cpSubsystemConfig.setSessionTimeToLiveSeconds(5)
  config.setCPSubsystemConfig(cpSubsystemConfig)

  val hz = Hazelcast.newHazelcastInstance(config)
  hz.getLifecycleService.addLifecycleListener(new NodeLifecycleListener)

  val adsTopic = hz.getTopic[Advert](Topic.ads)

  implicit val actorSystem = ActorSystem()

  import akka.http.scaladsl.server.Directives._
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
  SingletonTask.currentWeather(hz)
}
