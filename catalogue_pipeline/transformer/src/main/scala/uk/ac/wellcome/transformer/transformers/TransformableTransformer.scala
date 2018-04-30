package uk.ac.wellcome.transformer.transformers

import uk.ac.wellcome.models.transformable.Transformable
import uk.ac.wellcome.models.work.internal.UnidentifiedWork

import scala.reflect.ClassTag
import scala.util.Try

trait TransformableTransformer[T <: Transformable] {
  protected[this] def transformForType(
    t: T,
    version: Int): Try[Option[UnidentifiedWork]]

  def transform(transformable: Transformable, version: Int)(
    implicit tag: ClassTag[T]): Try[Option[UnidentifiedWork]] =
    Try {
      transformable match {
        case t: T => transformForType(t, version)
        case _ => throw new RuntimeException(s"$transformable is not of the right type")
      }
    }.flatten
}
