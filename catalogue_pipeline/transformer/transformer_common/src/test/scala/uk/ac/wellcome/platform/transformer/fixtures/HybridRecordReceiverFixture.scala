package uk.ac.wellcome.platform.transformer.fixtures

import com.amazonaws.services.sns.AmazonSNS
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.test.fixtures.{Messaging, SNS}
import uk.ac.wellcome.messaging.test.fixtures.SNS.Topic
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.receive.HybridRecordReceiver
import uk.ac.wellcome.storage.ObjectStore
import uk.ac.wellcome.storage.fixtures.S3.Bucket
import uk.ac.wellcome.test.fixtures.TestWith

import scala.concurrent.ExecutionContext.Implicits.global

trait HybridRecordReceiverFixture extends Messaging with SNS {
  def withHybridRecordReceiver[T, R](
    topic: Topic,
    bucket: Bucket,
    snsClient: AmazonSNS = snsClient
  )(testWith: TestWith[HybridRecordReceiver[T], R])(
    implicit objectStore: ObjectStore[T]): R =
    withMessageWriter[TransformedBaseWork, R](
      bucket,
      topic,
      writerSnsClient = snsClient) { messageWriter =>
      val recordReceiver = new HybridRecordReceiver[T](
        messageWriter = messageWriter,
        objectStore = objectStore
      )

      testWith(recordReceiver)
    }
}