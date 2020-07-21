package com.example.imperative

import java.time.LocalDateTime

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

    // we execute a continuous query which will only receive updates for cache keys stored on local node (due to setLocal(true))
    // NOTE: this returns a cursor which should be closed to free resources normally
    currentWeatherMap.query(
      new ContinuousQuery[String, CurrentWeather]
        .setLocalListener(new WeatherChangeListener(ignite))
        .setLocal(true),
    )

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
