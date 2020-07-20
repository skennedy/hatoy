package com.example.imperative

import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

import javax.cache.event.CacheEntryEvent
import org.apache.ignite.Ignite
import org.apache.ignite.cache.query.ContinuousQuery
import org.apache.ignite.services.Service
import org.apache.ignite.services.ServiceContext
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

class SingletonTask(interval: FiniteDuration)(logic: () => Unit) extends Service {

  val logger = LoggerFactory.getLogger(this.getClass)

  override def init(ctx: ServiceContext): Unit = ()

  override def cancel(ctx: ServiceContext): Unit = ()

  override def execute(ctx: ServiceContext): Unit =
    while (!ctx.isCancelled) {
      logic()
      Thread.sleep(interval.toMillis)
    }
}
object SingletonTask {
  def currentWeather(ignite: Ignite): Unit = {
    val cities            = List("bari", "barcelona", "athens", "london", "paris", "chania", "new-york")
    val currentWeatherMap = ignite.getOrCreateCache[String, CurrentWeather](Maps.currentWeather)

    // we execute a continuous query on every node in the cluster
    // this will receive *all* updates on the cache by default
    val events = new ContinuousQuery[String, CurrentWeather]
    events.setLocalListener(new WeatherChangeListener(ignite))

    // this "remote filter" means that only updates written on the current node will trigger this query
    // this is a weird way to hide duplicate updates - would make more sense for the continuous query to only run on one node.
    val currentNode = ignite.cluster().localNode()
    events.setRemoteFilterFactory(() =>
      (_: CacheEntryEvent[_ <: String, _ <: CurrentWeather]) => currentNode.id.equals(ignite.cluster.localNode.id),
    )

    // actually run the query against the map
    // - this outputs a cursor to see the current state, but we only care about ongoing updates
    currentWeatherMap.query(events).close()

    // "services" are a weird way of deploying executable threads of code, which we leverage to have a singleton
    // TODO: investigate scheduling
    ignite.services().deployClusterSingleton(
      Locks.currentWeather,
      new SingletonTask(30.seconds)(() =>
        for (city <- cities) {

          val conditions = scala.util.Random.shuffle(Conditions.values).head
          currentWeatherMap.put(
            city,
            CurrentWeather(city, conditions, ignite.cluster().localNode().id(), LocalDateTime.now()),
          )
        },
      ),
    )
  }
}
