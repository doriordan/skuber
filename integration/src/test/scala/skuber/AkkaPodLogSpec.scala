package skuber

import akka.stream.scaladsl.TcpIdleTimeoutException
import skuber.akkaclient.AkkaKubernetesClient
import skuber.model.Pod.LogQueryParams

import java.time.ZonedDateTime

class AkkaPodLogSpec extends PodLogSpec with AkkaK8SFixture {

  it should "get log of a pod" in { k8s =>
    val akkaK8s = k8s.asInstanceOf[AkkaKubernetesClient]
    akkaK8s.getPodLogSource(podName, LogQueryParams(follow = Some(false))).flatMap { source =>
      source.map(_.utf8String).runReduce(_ + _).map { s =>
        assert(s == "foo\n")
      }
    }
  }

  it should "tail log of a pod and timeout after a while" in { k8s =>
    val akkaK8s = k8s.asInstanceOf[AkkaKubernetesClient]
    var log = ""
    val start = ZonedDateTime.now()
    akkaK8s.getPodLogSource(podName, LogQueryParams(follow = Some(true))).flatMap { source =>
      source.map(_.utf8String).runForeach(log += _)
    }.failed.map { case e: TcpIdleTimeoutException =>
      val msgPattern = s"TCP idle-timeout encountered on connection to [^,]+, no bytes passed in the last ${idleTimeout}"
      assert(e.getMessage.matches(msgPattern), s"""["${e.getMessage}"] does not match ["${msgPattern}"]""")
      assert(log == "foo\n")
      assert(ZonedDateTime.now().isAfter(start.withSecond(idleTimeout.toSeconds.toInt)))
    }
  }
}
