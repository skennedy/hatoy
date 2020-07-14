package com.example.imperative

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.TextMessage
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import com.hazelcast.topic.ITopic

object AdvertsFlow {
  def apply(adsTopic: ITopic[Advert])(implicit as: ActorSystem): Flow[Message, Message, NotUsed] = {
    val source = Source.fromGraph(new HZTopicSource(adsTopic)).map(ad => TextMessage(ad.toString): Message)
    Flow.fromSinkAndSourceCoupled[Message, Message](Sink.foreach(println(">>> ", _)), source)
  }
}
