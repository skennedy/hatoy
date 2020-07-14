package com.example.imperative

import akka.stream.Attributes
import akka.stream.Outlet
import akka.stream.SourceShape
import akka.stream.stage.GraphStage
import akka.stream.stage.GraphStageLogic
import akka.stream.stage.OutHandler
import com.hazelcast.topic.ITopic
import com.hazelcast.topic.Message
import com.hazelcast.topic.MessageListener
import org.slf4j.LoggerFactory

class HZTopicSource(topic: ITopic[Advert]) extends GraphStage[SourceShape[Advert]] {
  val out: Outlet[Advert] = Outlet("HZTopicSource.out")

  override val shape: SourceShape[Advert] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      val logger = LoggerFactory.getLogger(this.getClass)
      val callback = getAsyncCallback[Advert] { ad =>
        logger.debug(s"pushing out ad: $ad")
        push(out, ad)
      }

      override def preStart(): Unit =
        topic.addMessageListener(
          new MessageListener[Advert] {
            override def onMessage(message: Message[Advert]): Unit = {
              logger.debug(s"""Got topic message ${message.getMessageObject}, envelope: $message""")
              callback.invoke(message.getMessageObject)
            }
          },
        )
      setHandler(out,
                 new OutHandler {
                   override def onPull() = ()
                 },
      )
    }

}
