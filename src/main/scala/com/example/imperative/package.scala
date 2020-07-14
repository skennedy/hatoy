package com.example

import java.time.LocalDateTime
import java.util.UUID

import enumeratum._

package object imperative {
  object Maps {
    val currentWeather = "current-weather-by-city"
  }
  object Locks {
    val currentWeather = "current-weather-task"
  }
  object Topic {
    val ads = "ads"
  }

  sealed trait Conditions extends EnumEntry
  object Conditions extends Enum[Conditions] {
    final case object Rainy  extends Conditions
    final case object Sunny  extends Conditions
    final case object Cloudy extends Conditions
    override val values = findValues
  }

  final case class CurrentWeather(city: String, conditions: Conditions, computedBy: UUID, computedAt: LocalDateTime)
  final case class Advert(city: String, msg: String)
}
