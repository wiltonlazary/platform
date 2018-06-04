package uk.ac.wellcome.platform.goobi_reader.services

import java.io.{ByteArrayInputStream, InputStream}
import java.time.Instant
import java.time.temporal.ChronoUnit

import akka.actor.ActorSystem
import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client}
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.FunSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import uk.ac.wellcome.exceptions.GracefulFailureException
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.{SQSConfig, SQSStream}
import uk.ac.wellcome.messaging.test.fixtures.SQS.Queue
import uk.ac.wellcome.monitoring.MetricsSender
import uk.ac.wellcome.monitoring.test.fixtures.MetricsSenderFixture
import uk.ac.wellcome.platform.goobi_reader.GoobiRecordMetadata
import uk.ac.wellcome.platform.goobi_reader.fixtures.GoobiReaderFixtures
import uk.ac.wellcome.storage.dynamo._
import uk.ac.wellcome.storage.s3.S3StreamStore
import uk.ac.wellcome.storage.test.fixtures.LocalDynamoDb.Table
import uk.ac.wellcome.storage.test.fixtures.S3.Bucket
import uk.ac.wellcome.storage.vhs.{HybridRecord, VersionedHybridStore}
import uk.ac.wellcome.test.fixtures.{Akka, TestWith, fixture}
import uk.ac.wellcome.test.utils.ExtendedPatience

import scala.concurrent.duration._

class GoobiReaderWorkerServiceTest
  extends FunSpec
    with Akka
    with MetricsSenderFixture
    with ScalaFutures
    with ExtendedPatience
    with MockitoSugar
    with GoobiReaderFixtures {

  private val id = "mets-0001"
  private val goobiS3Prefix = "goobi"
  private val sourceKey = s"$id.xml"
  private val contents = "muddling the machinations of morose METS"
  private val updatedContents = "Updated contents"
  private val eventTime = Instant.parse("2018-01-01T01:00:00.000Z")
  private val contentsStorageKey = s"$goobiS3Prefix/10/$id/cd92f8d3"
  private val updatedContentsStorageKey = s"$goobiS3Prefix/10/$id/536b51f7"

  it("processes a notification") {
  withGoobiReaderWorkerService(s3Client) { case (bucket, queue, table, workerService) =>
      s3Client.putObject(bucket.name, sourceKey, contents)

      workerService.processMessage(
        aNotificationMessage(
          topicArn = queue.arn,
          message = s3Notification(sourceKey, bucket.name, eventTime)
        ))

      eventually {
        val expectedRecord = HybridRecord(
          id = id,
          version = 1,
          s3key = contentsStorageKey
          )
        assertRecordStored(expectedRecord, GoobiRecordMetadata(eventTime), contents, table, bucket)
      }
    }
  }

  it("doesn't overwrite a newer work with an older work") {
  withGoobiReaderWorkerServiceAndVhs(s3Client) { case (bucket, queue, table, vhs, workerService) =>
    val contentStream = new ByteArrayInputStream(contents.getBytes)
    vhs.updateRecord(id = id)(
      ifNotExisting = (contentStream, GoobiRecordMetadata(eventTime)))(
      ifExisting = (t, m) => (t, m))
    eventually {
      val expectedRecord = HybridRecord(
        id = id,
        version = 1,
        s3key = contentsStorageKey
      )
      val expectedMetadata = GoobiRecordMetadata(eventTime)
      assertRecordStored(expectedRecord, expectedMetadata, contents, table, bucket)
    }


      s3Client.putObject(bucket.name, sourceKey, contents)

      val olderEventTime = eventTime.minus(1, ChronoUnit.HOURS)
      val message = aNotificationMessage(
        topicArn = queue.arn,
        message = s3Notification(sourceKey, bucket.name, olderEventTime))

      workerService.processMessage(message)

      eventually {
        val expectedRecord = HybridRecord(
          id = id,
          version = 1,
          s3key = contentsStorageKey
        )
        val expectedMetadata = GoobiRecordMetadata(eventTime)
        assertRecordStored(expectedRecord, expectedMetadata, contents, table, bucket)
      }
    }
  }

  it("overwrites an older work with an newer work") {
    withGoobiReaderWorkerServiceAndVhs(s3Client) { case (bucket, queue, table, vhs, workerService) =>
      val contentStream = new ByteArrayInputStream(contents.getBytes)
      vhs.updateRecord(id = id)(
        ifNotExisting = (contentStream, GoobiRecordMetadata(eventTime)))(
        ifExisting = (t, m) => (t, m))
      eventually {
        val expectedRecord = HybridRecord(
          id = id,
          version = 1,
          s3key = contentsStorageKey
        )
        assertRecordStored(expectedRecord, GoobiRecordMetadata(eventTime), contents, table, bucket)
      }

      s3Client.putObject(bucket.name, sourceKey, updatedContents)
      val newerEventTime = eventTime.plus(1, ChronoUnit.HOURS)
      val message = aNotificationMessage(
        topicArn = queue.arn,
        message = s3Notification(sourceKey, bucket.name, newerEventTime))

      workerService.processMessage(message)

      eventually {
        val expectedRecord = HybridRecord(
          id = id,
          version = 2,
          s3key = updatedContentsStorageKey
        )
        val expectedMetadata = GoobiRecordMetadata(newerEventTime)
        assertRecordStored(expectedRecord, expectedMetadata, updatedContents, table, bucket)
      }
    }
  }

  it("fails with a GracefulFailureException if Json cannot be parsed") {
    withGoobiReaderWorkerService(s3Client) { case (_, queue, _, workerService) =>
      val notificationMessage = aNotificationMessage(
        topicArn = queue.arn,
        message = "NotJson"
      )
      whenReady(workerService.processMessage(notificationMessage).failed) { actualException =>
        actualException shouldBe a[GracefulFailureException]
        actualException.getMessage shouldBe "Unable to parse Json String."
      }
    }
  }

  it("fails with a GracefulFailureException if content cannot be fetched") {
    withGoobiReaderWorkerService(s3Client) { case (bucket, queue, _, workerService) =>
      val sourceKey = "NotThere.xml"

      val notificationMessage = aNotificationMessage(
        topicArn = queue.arn,
        message = s3Notification(sourceKey, bucket.name, eventTime)
      )
      whenReady(workerService.processMessage(notificationMessage).failed) { actualException =>
        actualException shouldBe a[GracefulFailureException]
        actualException.getMessage should include("The specified key does not exist")
      }
    }
  }

  it("fails with a GracefulFailureException if there is an unexpected failure") {
    val mockClient = mock[AmazonS3Client]
    val expectedException = new RuntimeException("Failed!")
    when(mockClient.getObject(any[String], any[String])).thenThrow(expectedException)
    withGoobiReaderWorkerService(mockClient) { case (bucket, queue, _, workerService) =>
      val sourceKey = "any.xml"
      val notificationMessage = aNotificationMessage(
        topicArn = queue.arn,
        message = s3Notification(sourceKey, bucket.name, eventTime)
      )
      whenReady(workerService.processMessage(notificationMessage).failed) { actualException =>
        actualException shouldBe a[GracefulFailureException]
        actualException.getMessage shouldBe "Failed!"
      }
    }
  }

  private def assertRecordStored(expectedRecord: HybridRecord, expectedMetadata: GoobiRecordMetadata, expectedContents: String, table: Table, bucket: Bucket) = {
    val id = expectedRecord.id
    getHybridRecord(table, id) shouldBe expectedRecord
    getRecordMetadata[GoobiRecordMetadata](table, id).toString shouldBe expectedMetadata.toString
    getContentFromS3(bucket, getHybridRecord(table, id).s3key) shouldBe expectedContents
  }

  private def withS3StreamStoreFixtures[R](testWith: TestWith[(Bucket,
    Table,
    VersionedHybridStore[InputStream, GoobiRecordMetadata, S3StreamStore]),
    R]): R =
    withLocalS3Bucket[R] { bucket =>
      withLocalDynamoDbTable[R] { table =>
        withStreamVHS[GoobiRecordMetadata, R](bucket, table, goobiS3Prefix) { vhs =>
          testWith((bucket, table, vhs))
        }
      }
    }

  private def withSqsStream[R](actorSystem: ActorSystem, queue: Queue, metricsSender: MetricsSender) =
    fixture[SQSStream[NotificationMessage], R](
      new SQSStream(
        actorSystem = actorSystem,
        sqsClient = asyncSqsClient,
        sqsConfig = SQSConfig(
          queueUrl = queue.url,
          waitTime = 1.second,
          maxMessages = 1
        ),
        metricsSender = metricsSender
      )
    )

  private def withGoobiReaderWorkerService[R](s3Client: AmazonS3)(testWith: TestWith[(Bucket, Queue, Table, GoobiReaderWorkerService), R]): R =
    withActorSystem { actorSystem =>
      withLocalSqsQueue { queue =>
        withMetricsSender(actorSystem) { metricsSender =>
          withSqsStream(actorSystem, queue, metricsSender) { sqsStream =>
            withS3StreamStoreFixtures { case (bucket, table, vhs) =>
              testWith((bucket, queue, table, new GoobiReaderWorkerService(s3Client, actorSystem, sqsStream, vhs)))
            }
          }
        }
      }
    }

  private def withGoobiReaderWorkerServiceAndVhs[R](s3Client: AmazonS3)(testWith: TestWith[
    (Bucket, Queue, Table, VersionedHybridStore[InputStream, GoobiRecordMetadata, S3StreamStore], GoobiReaderWorkerService), R]): R =
    withActorSystem { actorSystem =>
      withLocalSqsQueue { queue =>
        withMetricsSender(actorSystem) { metricsSender =>
          withSqsStream(actorSystem, queue, metricsSender) { sqsStream =>
            withS3StreamStoreFixtures { case (bucket, table, vhs) =>
              testWith((bucket, queue, table, vhs, new GoobiReaderWorkerService(s3Client, actorSystem, sqsStream, vhs)))
            }
          }
        }
      }
    }
}
