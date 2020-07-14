package com.example.imperative

import com.hazelcast.core.EntryEvent
import com.hazelcast.core.HazelcastInstance
import com.hazelcast.map.listener.EntryUpdatedListener
import org.slf4j.LoggerFactory

class WeatherChangeListener(hz: HazelcastInstance) extends EntryUpdatedListener[String, CurrentWeather] {
  protected val logger = LoggerFactory.getLogger(this.getClass)
  override def entryUpdated(event: EntryEvent[String, CurrentWeather]): Unit = {
    val topic = hz.getTopic[Advert](Topic.ads)

    val previous = event.getOldValue.conditions
    val current  = event.getValue.conditions

    if (current == Conditions.Cloudy && current != previous) {
      val ad = Advert(event.getKey, "Buy this umbrella!")
      logger.info(s"Publishing advert: $ad")
      topic.publish(ad)
    }
    if (event.getKey == "london" && current == Conditions.Sunny && current != previous) {
      val ad = Advert(event.getKey, "Buy a disposable BBQ!")
      logger.info(s"Publishing advert: $ad")
      topic.publish(ad)
    }
  }
}
