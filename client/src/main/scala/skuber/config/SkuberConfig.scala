package skuber.config

import com.typesafe.config.{Config, ConfigFactory}
import skuber.config.SkuberConfig.skuberKeyPath
import scala.concurrent.duration.Duration
import scala.util.Try

case class SkuberConfig(appConfig: Config) {
  def getSkuberConfig[T](key: String, fromConfig: String => Option[T], default: T): T = {
    val skuberConfigKey = s"$skuberKeyPath.$key"
    Try(appConfig.getAnyRef(skuberConfigKey)).toOption match {
      case Some(_) =>
        fromConfig(skuberConfigKey) match {
          case None => default
          case Some(t) => t
        }
      case None => default
    }
  }

  def getRootSkuberConfig: Config = appConfig.getConfig(skuberKeyPath)

  def getDuration(configKey: String, default: Duration): Duration = getSkuberConfig(configKey, durationFromConfig, default)

  def durationFromConfig(configKey: String): Option[Duration] = Some(Duration.fromNanos(appConfig.getDuration(configKey).toNanos))
}

object SkuberConfig {
  final val skuberKeyPath = "skuber"

  def load(appConfig: Config = ConfigFactory.load()): SkuberConfig = {
    appConfig.checkValid(ConfigFactory.defaultReference(), skuberKeyPath)
    SkuberConfig(appConfig)
  }
}
