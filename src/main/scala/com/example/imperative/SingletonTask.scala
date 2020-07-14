package com.example.imperative

import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import com.hazelcast.core.HazelcastInstance
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

class SingletonTask(hz: HazelcastInstance, interval: FiniteDuration, lockId: String)(logic: () => Unit) {
  private val executor = Executors.newSingleThreadScheduledExecutor()
  val logger           = LoggerFactory.getLogger(this.getClass)

  executor.scheduleAtFixedRate(
    () => {
      val lock = hz.getCPSubsystem.getLock(lockId)
      if (lock.tryLock()) {
        logger.debug(s"got lock $lockId")
        logic()
      } else {
        logger.debug(s"Couldn't get hold of lock $lockId")
      }
    },
    0,
    interval.toMillis,
    TimeUnit.MILLISECONDS,
  )
}
object SingletonTask {
  def currentWeather(hz: HazelcastInstance): SingletonTask = {
    val cities            = List("bari", "barcelona", "athens", "london", "paris", "chania", "new-york")
    val currentWeatherMap = hz.getMap[String, CurrentWeather](Maps.currentWeather)

    //executes only on the partition that owns the map key
    currentWeatherMap.addLocalEntryListener(new WeatherChangeListener(hz))

    new SingletonTask(hz, 30.seconds, Locks.currentWeather)(() =>
      for (city <- cities) {
        val conditions = scala.util.Random.shuffle(Conditions.values).head
        currentWeatherMap.put(
          city,
          CurrentWeather(city, conditions, hz.getCluster.getLocalMember.getUuid, LocalDateTime.now()),
        )
      },
    )
  }
}
