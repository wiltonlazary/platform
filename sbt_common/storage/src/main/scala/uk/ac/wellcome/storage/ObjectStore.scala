package uk.ac.wellcome.storage

import uk.ac.wellcome.storage.type_classes.StorageStrategy
import scala.concurrent.{ExecutionContext, Future}


case class KeyPrefix(value: String) extends AnyVal
case class KeySuffix(value: String) extends AnyVal

trait ObjectStore[T] {
  def put(namespace: String)(
    input: T,
    keyPrefix: KeyPrefix = KeyPrefix(""),
    keySuffix: KeySuffix = KeySuffix(""),
    userMetadata: Map[String, String] = Map()
  )(
    implicit
      storageStrategy: StorageStrategy[T],
      storageBackend: StorageBackend
  ): Future[ObjectLocation]

  def get(objectLocation: ObjectLocation)(
    implicit
      storageStrategy: StorageStrategy[T],
      storageBackend: StorageBackend
  ): Future[T]
}

object ObjectStore {

  private def normalizePathFragment(prefix: String) =
    prefix
      .stripPrefix("/")
      .stripSuffix("/")

  def apply[T](implicit store: ObjectStore[T]): ObjectStore[T] =
    store

  def createObjectStore[T](
    put: ((String), (T, KeyPrefix, KeySuffix, Map[String, String]), (StorageStrategy[T])) =>
      Future[ObjectLocation],
    get: ((ObjectLocation), (StorageStrategy[T])) => Future[T]
  )(implicit
      storageStrategy: StorageStrategy[T],
      storageBackend: StorageBackend
  ): ObjectStore[T] = new ObjectStore[T] {
    def put(namespace: String)(
      t: T,
      keyPrefix: KeyPrefix = KeyPrefix(""),
      keySuffix: KeySuffix = KeySuffix(""),
      userMetadata: Map[String, String] = Map()
    )(implicit
        storageStrategy: StorageStrategy[T],
        storageBackend: StorageBackend
    ): Future[ObjectLocation] = {
      val storageStream = storageStrategy.store(t)

      val prefix = normalizePathFragment(keyPrefix.value)
      val suffix = normalizePathFragment(keySuffix.value)
      val storageKey = storageStream.storageKey.value

      val key = s"$prefix/${storageKey}$suffix"

      val location = ObjectLocation(namespace, key)

      val stored = storageBackend.put(
        location,
        storageStream.inputStream,
        userMetadata
      )

      stored.map(_ => location)
    }

    def get(objectLocation: ObjectLocation)(
      implicit
        storageStrategy: StorageStrategy[T],
        storageBackend: StorageBackend
    ): Future[T] = {
      val input = storageBackend.get(objectLocation)
      input.map(storageStrategy.retrieve)
    }
  }

}