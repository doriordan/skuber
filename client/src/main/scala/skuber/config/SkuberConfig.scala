package skuber.config

import com.typesafe.config.{Config, ConfigFactory}
import skuber.config.SkuberConfig.skuberKeyPath

import scala.concurrent.duration.Duration

case class SkuberConfig(appConfig: Config) {
  def getSkuberConfig[T](key: String, fromConfig: String => Option[T], default: T): T = {
    val skuberConfigKey = s"$skuberKeyPath.$key"
    if (appConfig.getIsNull(skuberConfigKey)) {
      default
    } else {
      fromConfig(skuberConfigKey) match {
        case None => default
        case Some(t) => t
      }
    }
  }

  def getDuration(configKey: String, default: Duration = Duration.Inf): Duration = getSkuberConfig(configKey, durationFromConfig, default)
  def durationFromConfig(configKey: String): Option[Duration] = Some(Duration.fromNanos(appConfig.getDuration(configKey).toNanos))
}

object SkuberConfig {
  final val skuberKeyPath = "skuber"

  def load(appConfig: Config = ConfigFactory.load()): SkuberConfig = {
    appConfig.checkValid(ConfigFactory.defaultReference(), skuberKeyPath)
    SkuberConfig(appConfig)
  }
}
