package uk.ac.wellcome.platform.sierra_items_to_dynamo.services

import com.google.inject.Inject
import uk.ac.wellcome.models.transformable.sierra.SierraItemRecord
import uk.ac.wellcome.platform.sierra_items_to_dynamo.merger.SierraItemRecordMerger
import uk.ac.wellcome.storage.ObjectStore
import uk.ac.wellcome.storage.dynamo._
import uk.ac.wellcome.storage.vhs.{EmptyMetadata, VersionedHybridStore}

import scala.concurrent.Future

class DynamoInserter @Inject()(
  versionedHybridStore: VersionedHybridStore[SierraItemRecord,
                                             EmptyMetadata,
                                             ObjectStore[SierraItemRecord]]) {

  def insertIntoDynamo(record: SierraItemRecord): Future[Unit] =
    versionedHybridStore.updateRecord(
      id = record.id.withoutCheckDigit
    )(
      ifNotExisting = (record, EmptyMetadata())
    )(
      ifExisting = (existingRecord, existingMetadata) =>
        (
          SierraItemRecordMerger
            .mergeItems(
              existingRecord = existingRecord,
              updatedRecord = record),
          existingMetadata
      )
    )
}
