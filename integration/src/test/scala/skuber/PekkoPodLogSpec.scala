package skuber

import org.apache.pekko.stream.scaladsl.TcpIdleTimeoutException
import skuber.model.Pod.LogQueryParams
import skuber.pekkoclient.PekkoKubernetesClient

import java.time.ZonedDateTime

class PekkoPodLogSpec extends PodLogSpec with PekkoK8SFixture  {

  it should "get log of a pod" in {
    withPekkoK8sClient { k8s =>
      k8s.getPodLogSource(podName, LogQueryParams(follow = Some(false))).flatMap { source =>
        source.map(_.utf8String).runReduce(_ + _).map { s =>
          assert(s == "foo\n")
        }
      }
    }
  }

  it should "tail log of a pod and timeout after a while" in {
    withPekkoK8sClient { k8s =>
      var log = ""
      val start = ZonedDateTime.now()
      k8s.getPodLogSource(podName, LogQueryParams(follow = Some(true))).flatMap { source =>
        source.map(_.utf8String).runForeach(log += _)
      }.failed.map { case e: TcpIdleTimeoutException =>
        val msgPattern = s"TCP idle-timeout encountered on connection to [^,]+, no bytes passed in the last ${idleTimeout}"
        assert(e.getMessage.matches(msgPattern), s"""["${e.getMessage}"] does not match ["${msgPattern}"]""")
        assert(log == "foo\n")
        assert(ZonedDateTime.now().isAfter(start.withSecond(idleTimeout.toSeconds.toInt)))
      }
    }
  }
}
