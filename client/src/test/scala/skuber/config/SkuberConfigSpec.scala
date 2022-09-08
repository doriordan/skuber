package skuber.config

import com.typesafe.config.ConfigFactory
import org.specs2.mutable.Specification

import scala.concurrent.duration.{Duration, DurationInt}

class SkuberConfigSpec extends Specification {
  "This is a specification for the 'SkuberConfigSpec' class".txt

  "SkuberConfig" should {
    "in-cluster" should {
      "refresh token interval defaults to 5 min if no configuration provided" in {
        val refreshTokenInterval = SkuberConfig.load().getDuration("in-cluster.refresh-token-interval")
        refreshTokenInterval shouldEqual 5.minutes
      }

      "refresh token interval value provided by the configuration" in {
        val appConfig = ConfigFactory.parseString(
          """
            |skuber.in-cluster.refresh-token-interval = 100ms
        """.stripMargin)
          .withFallback(ConfigFactory.load())

        val refreshTokenInterval = SkuberConfig.load(appConfig).getDuration("in-cluster.refresh-token-interval")
        refreshTokenInterval shouldEqual 100.milliseconds
      }
    }
    "watch-continuously" should {
      "defaults are provided" in {
        val skuberConfig = SkuberConfig.load()
        val watchContinuouslyRequestTimeout: Duration = skuberConfig.getDuration("watch-continuously.request-timeout")
        watchContinuouslyRequestTimeout shouldEqual 30.seconds

        val watchContinuouslyIdleTimeout: Duration = skuberConfig.getDuration("watch-continuously.idle-timeout")
        watchContinuouslyIdleTimeout shouldEqual 60.seconds

        val watchPoolIdleTimeout: Duration = skuberConfig.getDuration("watch-continuously.pool-idle-timeout")
        watchPoolIdleTimeout shouldEqual 30.seconds
      }
    }
  }
}
