package uk.ac.wellcome.platform.archive.archivist.flow

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}
import com.amazonaws.services.s3.AmazonS3
import grizzled.slf4j.Logging
import uk.ac.wellcome.platform.archive.archivist.models.errors.{ArchiveItemJobError, ArchiveJobError}
import uk.ac.wellcome.platform.archive.archivist.models.{ArchiveDigestItemJob, ArchiveItemJob, ArchiveJob}
import uk.ac.wellcome.platform.archive.common.flows.FoldEitherFlow
import uk.ac.wellcome.platform.archive.common.models.{ArchiveComplete, BagItem, EntryPath, Parallelism}
import uk.ac.wellcome.platform.archive.common.models.error.ArchiveError

/** This flow extracts a tag manifest from a ZIP file, and uploads it to S3
  *
  * It emits the original archive job.
  *
  * It returns an error if:
  *   - There's a problem getting the item from the ZIP file
  *   - The upload to S3 fails
  *
  */
object ArchiveTagManifestFlow extends Logging {
  type ArchiveJobStep = Either[ArchiveError[_], ArchiveJob]

  def apply()(
    implicit s3Client: AmazonS3,
    parallelism: Parallelism
  ): Flow[ArchiveJob, ArchiveJobStep, NotUsed] =
    Flow[ArchiveJob]
            .map(archiveTagManifestItemJob)
            .via(UploadItemFlow(parallelism.value))
            .via(
              FoldEitherFlow[ArchiveError[ArchiveItemJob],
                (ArchiveItemJob, String), ArchiveJobStep](
                ifLeft = Flow[ArchiveError[ArchiveItemJob]].map { error =>
                  warn(error.toString)
                  Left(ArchiveItemJobError(error.t.archiveJob, List(error)))
                })(
                ifRight = Flow[(ArchiveItemJob, String)]
                  .map(context => archiveDigestItemJob _ tupled context)
                  .via(DownloadAndVerifyDigestItemFlow(parallelism.value))
                  .via(extractArchiveJobFlow)
              )
            )


  private def archiveTagManifestItemJob(
    archiveJob: ArchiveJob): ArchiveItemJob = {
    val tagManifestFileName = archiveJob.config.tagManifestFileName
    ArchiveItemJob(archiveJob, EntryPath(tagManifestFileName))
  }

  private def archiveDigestItemJob(
                                    archiveItemJob: ArchiveItemJob,
                                   digest: String
                                  ): ArchiveDigestItemJob =
    ArchiveDigestItemJob(
      archiveItemJob.archiveJob,
      BagItem(digest, archiveItemJob.itemLocation))

  private def extractArchiveJobFlow() = {
    FoldEitherFlow[
      ArchiveError[ArchiveDigestItemJob],
      ArchiveDigestItemJob,
      Either[ArchiveError[ArchiveJob], ArchiveJob]](
      ifLeft = Flow[ArchiveError[ArchiveDigestItemJob]].map { error =>
        warn(error.toString)
        Left(ArchiveJobError(error.t.archiveJob, List(error)))
      })(ifRight = Flow[ArchiveDigestItemJob].map { archiveDigestItemJob =>
      Right(archiveDigestItemJob.archiveJob)
    })
  }
}
