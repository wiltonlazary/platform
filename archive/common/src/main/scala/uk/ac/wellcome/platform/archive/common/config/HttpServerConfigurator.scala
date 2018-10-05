package uk.ac.wellcome.platform.archive.common.config

import org.rogach.scallop.ScallopConf
import uk.ac.wellcome.platform.archive.common.modules.config.HttpServerConfig

trait HttpServerConfigurator extends ScallopConf {
  val arguments: Seq[String]

  private val appPort =
    opt[Int](required = true, default = Some(9001))

  private val appHost =
    opt[String](required = true, default = Some("0.0.0.0"))

  verify()

  val httpServerConfig = HttpServerConfig(
    host = appHost(),
    port = appPort()
  )
}
