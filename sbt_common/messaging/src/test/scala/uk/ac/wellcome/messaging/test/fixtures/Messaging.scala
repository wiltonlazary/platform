package uk.ac.wellcome.messaging.test.fixtures

import akka.actor.ActorSystem
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.model.{SubscribeRequest, SubscribeResult}
import io.circe.Decoder
import io.circe.generic.semiauto._
import uk.ac.wellcome.messaging.message.{
  MessageConfig,
  MessageReader,
  MessageWorker,
  MessageWriter
}
import uk.ac.wellcome.messaging.sns.SNSConfig
import uk.ac.wellcome.messaging.sqs.{SQSConfig, SQSReader}
import uk.ac.wellcome.messaging.test.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.test.fixtures.SQS.Queue
import uk.ac.wellcome.metrics
import uk.ac.wellcome.storage.s3.{KeyPrefixGenerator, S3Config, S3ObjectStore}
import uk.ac.wellcome.storage.test.fixtures.S3
import uk.ac.wellcome.storage.test.fixtures.S3.Bucket
import uk.ac.wellcome.test.fixtures._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Future
import com.amazonaws.services.sns.model.UnsubscribeRequest

trait Messaging
    extends Akka
    with MetricsSender
    with SQS
    with SNS
    with S3
    with ImplicitLogging {

  def withLocalStackSubscription[R](queue: Queue, topic: Topic) =
    fixture[SubscribeResult, R](
      create = {
        val subRequest = new SubscribeRequest(topic.arn, "sqs", queue.arn)
        info(s"Subscribing queue ${queue.arn} to topic ${topic.arn}")

        localStackSnsClient.subscribe(subRequest)
      },
      destroy = { subscribeResult =>
        val unsubscribeRequest =
          new UnsubscribeRequest(subscribeResult.getSubscriptionArn)
        localStackSnsClient.unsubscribe(unsubscribeRequest)
      }
    )

  case class ExampleObject(name: String)

  class ExampleMessageWorker(
    sqsReader: SQSReader,
    messageReader: MessageReader[ExampleObject],
    actorSystem: ActorSystem,
    metricsSender: metrics.MetricsSender
  ) extends MessageWorker[ExampleObject](
        sqsReader,
        messageReader,
        actorSystem,
        metricsSender
      ) {

    var calledWith: Option[ExampleObject] = None
    def hasBeenCalled: Boolean = calledWith.nonEmpty

    override implicit val decoder: Decoder[ExampleObject] =
      deriveDecoder[ExampleObject]

    override def processMessage(message: ExampleObject) = Future {
      calledWith = Some(message)

      info("processMessage was called!")
    }
  }

  val keyPrefixGenerator: KeyPrefixGenerator[ExampleObject] =
    new KeyPrefixGenerator[ExampleObject] {
      override def generate(obj: ExampleObject): String = "/"
    }

  def withMessageReader[R](bucket: Bucket, topic: Topic)(
    testWith: TestWith[MessageReader[ExampleObject], R]) = {

    val s3Config = S3Config(bucketName = bucket.name)
    val snsConfig = SNSConfig(topicArn = topic.arn)

    val messageConfig = MessageConfig(
      s3Config = s3Config,
      snsConfig = snsConfig
    )

    val testReader = new MessageReader[ExampleObject](
      messageConfig = messageConfig,
      s3Client = s3Client,
      keyPrefixGenerator = keyPrefixGenerator
    )

    testWith(testReader)
  }

  def withMessageWorker[R](
    actorSystem: ActorSystem,
    metricsSender: metrics.MetricsSender,
    queue: Queue,
    messageReader: MessageReader[ExampleObject]
  )(testWith: TestWith[ExampleMessageWorker, R]) = {

    val sqsReader = new SQSReader(sqsClient, SQSConfig(queue.url, 1.second, 1))

    val testWorker = new ExampleMessageWorker(
      sqsReader,
      messageReader,
      actorSystem,
      metricsSender
    )

    try {
      testWith(testWorker)
    } finally {
      testWorker.stop()
    }
  }

  def withMessageWriter[R](bucket: Bucket,
                           topic: Topic,
                           writerSnsClient: AmazonSNS = snsClient)(
    testWith: TestWith[MessageWriter[ExampleObject], R]) = {
    val s3Config = S3Config(bucketName = bucket.name)
    val snsConfig = SNSConfig(topicArn = topic.arn)
    val messageConfig = MessageConfig(
      s3Config = s3Config,
      snsConfig = snsConfig
    )

    val messageWriter = new MessageWriter[ExampleObject](
      messageConfig = messageConfig,
      snsClient = writerSnsClient,
      s3Client = s3Client,
      keyPrefixGenerator = keyPrefixGenerator
    )

    testWith(messageWriter)
  }

  def withMessageReaderFixtures[R](
    testWith: TestWith[(Bucket, MessageReader[ExampleObject]), R]) = {
    withLocalS3Bucket { bucket =>
      withLocalSnsTopic { topic =>
        withMessageReader(bucket = bucket, topic = topic) { reader =>
          testWith((bucket, reader))
        }
      }
    }
  }

  def withMessageWorkerFixtures[R](
    testWith: TestWith[(metrics.MetricsSender,
                        Queue,
                        Bucket,
                        ExampleMessageWorker),
                       R]) = {
    withActorSystem { actorSystem =>
      withMetricsSender(actorSystem) { metricsSender =>
        withLocalStackSqsQueue { queue =>
          withMessageReaderFixtures {
            case (bucket, messageReader) =>
              withMessageWorker(
                actorSystem,
                metricsSender,
                queue,
                messageReader) { worker =>
                testWith((metricsSender, queue, bucket, worker))
              }
          }
        }
      }
    }
  }

  def withMessageWorkerFixturesAndMockedMetrics[R](
    testWith: TestWith[(metrics.MetricsSender,
                        Queue,
                        Bucket,
                        ExampleMessageWorker),
                       R]) = {
    withActorSystem { actorSystem =>
      withMockMetricSender { metricsSender =>
        withLocalStackSqsQueue { queue =>
          withMessageReaderFixtures {
            case (bucket, messageReader) =>
              withMessageWorker(
                actorSystem,
                metricsSender,
                queue,
                messageReader) { worker =>
                testWith((metricsSender, queue, bucket, worker))
              }
          }
        }
      }
    }
  }
}