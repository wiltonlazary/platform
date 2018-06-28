package uk.ac.wellcome.platform.transformer.transformers

import uk.ac.wellcome.models.transformable.Transformable
import uk.ac.wellcome.models.work.internal.TransformedBaseWork

import scala.util.Try

trait TransformableTransformer[T <: Transformable] {
  def transformForType: PartialFunction[(Transformable, Int), Try[Option[TransformedBaseWork]]]

  def transform(transformable: Transformable, version: Int): Try[Option[TransformedBaseWork]] =
    Try {
      transformable match {
        case t if transformForType.isDefinedAt((t, version))=> transformForType((t, version))
        case _ =>
          throw new RuntimeException(s"$transformable is not of the right type")
      }
    }.flatten
}
