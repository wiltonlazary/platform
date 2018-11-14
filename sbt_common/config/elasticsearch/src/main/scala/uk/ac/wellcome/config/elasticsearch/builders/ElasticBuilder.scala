package uk.ac.wellcome.config.elasticsearch.builders

import com.sksamuel.elastic4s.http.HttpClient
import com.typesafe.config.Config
import uk.ac.wellcome.config.core.builders.EnrichConfig._
import uk.ac.wellcome.elasticsearch.{DisplayElasticConfig, ElasticClientBuilder}

object ElasticBuilder {
  def buildHttpClient(config: Config): HttpClient = {
    val hostname = config.getOrElse[String]("es.host")(default = "localhost")
    val port = config.getOrElse[Int]("es.port")(default = 9200)
    val protocol = config.getOrElse[String]("es.protocol")(default = "http")
    val username = config.getOrElse[String]("es.username")(default = "username")
    val password = config.getOrElse[String]("es.password")(default = "password")

    ElasticClientBuilder.create(
      hostname = hostname,
      port = port,
      protocol = protocol,
      username = username,
      password = password
    )
  }

  def buildElasticConfig(config: Config): DisplayElasticConfig =
    DisplayElasticConfig(
      documentType = config.getOrElse[String]("es.type")(default = "item"),
      indexV1name = config.required[String]("es.index.v1"),
      indexV2name = config.required[String]("es.index.v2")
    )
}