package skuber.examples.podlogs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.util.ByteString
import skuber.model.{Container, Pod}
import skuber.json.format._
import skuber.api.client.{LoggingConfig, defaultAppConfig, defaultK8sConfig}
import skuber.akkaclient.k8sInit

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * @author David O'Riordan
  *
  * Demonstrate streaming of pod logs, in this example the log is just a couple of short line printed to stdout
  */
object PodLogExample extends App {

  def printLogFlow(cntrName: String): Sink[ByteString, NotUsed] = Flow[ByteString]
      .via(Framing.delimiter(
        ByteString("\n"),
        maximumFrameLength = 256,
        allowTruncation = true))
      .map(_.utf8String)
      .to(Sink.foreach(text => println(s"[${cntrName} logs] $text")))


  implicit val system = ActorSystem()
  implicit val dispatcher = system.dispatcher
  val k8s = k8sInit(
    defaultK8sConfig.currentContext,
    LoggingConfig(logRequestBasic = false, logResponseBasic = false),
    None,
    defaultAppConfig
  )

  val helloWorldContainer=Container(name="hello-world", image="busybox", command=List("sh", "-c", "echo Hello World! && echo Goodbye World && sleep 60"))
  val helloWorldContainer2=Container(name="hello-world2", image="busybox", command=List("sh", "-c", "echo Hello World again! && echo Goodbye World again && sleep 60"))
  val helloWorldPod=Pod("hello-world", Pod.Spec().addContainer(helloWorldContainer).addContainer(helloWorldContainer2))

  val podFut = k8s.create(helloWorldPod)

  println("Waiting 30 seconds to allow pod initialisation to complete before getting logs...")
  Thread.sleep(30000)
  for {
    pod <- podFut
    logsSource <- k8s.getPodLogSource("hello-world", Pod.LogQueryParams(containerName = Some("hello-world"), sinceSeconds = Some(9999999)))
    logsSource1 <- k8s.getPodLogSource("hello-world", Pod.LogQueryParams(containerName = Some("hello-world2"), sinceTime = pod.metadata.creationTimestamp))
    donePrinting = logsSource.runWith(printLogFlow("hello-world"))
    donePrinting1 = logsSource1.runWith(printLogFlow("hello-world2"))
  } yield (donePrinting, donePrinting1)

  // allow another 5 seconds for logs to be streamed from the pod to stdout before cleaning up
  Thread.sleep(5000)
  Await.result(k8s.delete[Pod]("hello-world"), 5.seconds)
  k8s.close()
  system.terminate()
  System.exit(0)
}
