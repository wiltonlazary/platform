package uk.ac.wellcome.platform.archiver.flow

import akka.stream.scaladsl.Flow
import grizzled.slf4j.Logging
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.storage.ObjectLocation

object BagLocationFromNotificationFlow extends Logging {
  def apply() = {
    Flow[NotificationMessage]
      .log("notification message")
      .map((m: NotificationMessage) => fromJson[ObjectLocation](m.Message))
      .map(_.get)
      .log("download location")
  }
}