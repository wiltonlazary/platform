package uk.ac.wellcome.storage.s3

import java.net.URI

import scala.util.Try

case class S3ObjectLocation(bucket: String, key: String)