package uk.ac.wellcome.platform.merger.fixtures

import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.test.fixtures.Messaging
import uk.ac.wellcome.messaging.test.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.test.fixtures.SQS.Queue
import uk.ac.wellcome.models.work.internal.BaseWork
import uk.ac.wellcome.monitoring.MetricsSender
import uk.ac.wellcome.platform.merger.services.{
  Merger,
  MergerManager,
  MergerWorkerService,
  RecorderPlaybackService
}
import uk.ac.wellcome.storage.fixtures.S3
import uk.ac.wellcome.test.fixtures.TestWith

import scala.concurrent.ExecutionContext.Implicits.global

trait WorkerServiceFixture extends LocalWorksVhs with Messaging with S3 {
  def withWorkerService[R](vhs: TransformedBaseWorkVHS,
                           topic: Topic,
                           queue: Queue,
                           metricsSender: MetricsSender)(
    testWith: TestWith[MergerWorkerService, R]): R =
    withLocalS3Bucket { messageBucket =>
      withMessageWriter[BaseWork, R](messageBucket, topic) { messageWriter =>
        withActorSystem { actorSystem =>
          withSQSStream[NotificationMessage, R](
            actorSystem,
            queue,
            metricsSender) { sqsStream =>
            val workerService = new MergerWorkerService(
              sqsStream = sqsStream,
              playbackService = new RecorderPlaybackService(vhs),
              mergerManager = new MergerManager(new Merger()),
              messageWriter = messageWriter
            )

            workerService.run()

            testWith(workerService)
          }
        }
      }
    }

  def withWorkerService[R](
    vhs: TransformedBaseWorkVHS,
    topic: Topic,
    queue: Queue)(testWith: TestWith[MergerWorkerService, R]): R =
    withActorSystem { actorSystem =>
      withMetricsSender(actorSystem) { metricsSender =>
        withWorkerService(vhs, topic, queue, metricsSender) { workerService =>
          testWith(workerService)
        }
      }
    }
}