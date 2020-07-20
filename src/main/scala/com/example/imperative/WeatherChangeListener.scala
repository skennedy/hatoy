package com.example.imperative

import java.lang

import javax.cache.event.CacheEntryEvent
import javax.cache.event.CacheEntryUpdatedListener
import org.apache.ignite.Ignite
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters._

class WeatherChangeListener(ignite: Ignite) extends CacheEntryUpdatedListener[String, CurrentWeather] {
  protected val logger = LoggerFactory.getLogger(this.getClass)
  override def onUpdated(
    events: lang.Iterable[CacheEntryEvent[_ <: String, _ <: CurrentWeather]],
  ): Unit =
    events.asScala.foreach { event =>
      val msg = ignite.message()

      (Option(event.getOldValue), Option(event.getValue)) match {
        case (Some(old), Some(value)) =>
          val previous = old.conditions
          val current  = value.conditions

          if (current == Conditions.Cloudy && current != previous) {
            val ad = Advert(event.getKey, "Buy this umbrella!")
            logger.info(s"Publishing advert: $ad")
            msg.send(Topic.ads, ad)
          }
          if (event.getKey == "london" && current == Conditions.Sunny && current != previous) {
            val ad = Advert(event.getKey, "Buy a disposable BBQ!")
            logger.info(s"Publishing advert: $ad")
            msg.send(Topic.ads, ad)
          }
        case (o, v) =>
          logger.info(s"Ignoring update of $o -> $v")
      }

    }
}
