package com.example.imperative

import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import org.apache.ignite.Ignite
import org.apache.ignite.cache.query.ContinuousQuery
import org.apache.ignite.services.Service
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

class SingletonTask(ignite: Ignite, interval: FiniteDuration)(logic: () => Unit) {
  private val executor = Executors.newSingleThreadScheduledExecutor()
  val logger           = LoggerFactory.getLogger(this.getClass)

  executor.scheduleAtFixedRate(
    () =>
      // only execute logic on oldest node in cluster
      if (ignite.cluster().forOldest().node().id() == ignite.cluster().localNode().id()) {
        logger.debug(s"executing singleton task")
        logic()
      } else {
        logger.debug("Couldn't execute singleton task")
      },
    0,
    interval.toMillis,
    TimeUnit.MILLISECONDS,
  )
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

    new SingletonTask(ignite, 30.seconds)(() =>
      for (city <- cities) {

        val conditions = scala.util.Random.shuffle(Conditions.values).head
        currentWeatherMap.put(
          city,
          CurrentWeather(city, conditions, ignite.cluster().localNode().id(), LocalDateTime.now()),
        )
      },
    )
  }
}
