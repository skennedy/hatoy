package com.example

import java.util.UUID
import java.util.concurrent.TimeUnit

import com.hazelcast.core.HazelcastInstance

class MapMaker(hz: HazelcastInstance) {
  import MapMaker._
  private val map = hz.getReplicatedMap[Key, Value[UUID]]("my-map")

  def get[A](key: Key): Option[Value[UUID]] = Option(map.get(key))
  def set(key: Key, value: Value[UUID]) = map.put(key, value, 1000, TimeUnit.MILLISECONDS)
}
object MapMaker {
  final case class Key(name: String)
  final case class Value[A](value: A)
}
