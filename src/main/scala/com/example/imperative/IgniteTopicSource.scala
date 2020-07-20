package com.example.imperative

import java.util.UUID

import akka.stream.Attributes
import akka.stream.Outlet
import akka.stream.SourceShape
import akka.stream.stage.GraphStage
import akka.stream.stage.GraphStageLogic
import akka.stream.stage.OutHandler
import org.apache.ignite.IgniteMessaging
import org.apache.ignite.lang.IgniteBiPredicate
import org.slf4j.LoggerFactory

class IgniteTopicSource(msg: IgniteMessaging, topic: String) extends GraphStage[SourceShape[Advert]] {
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
        msg.localListen(
          topic,
          (_, message: AnyRef) => {
            logger.debug(s"""Got topic message ${message}""")
            message match {
              case a: Advert => callback.invoke(a)
            }
            true
          },
        )

      setHandler(out,
                 new OutHandler {
                   override def onPull() = ()
                 },
      )
    }

}
