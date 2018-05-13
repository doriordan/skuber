package skuber.examples.podlogs

import akka.NotUsed
import skuber._
import skuber.json.format._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.util.ByteString
import skuber.api.client

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * @author David O'Riordan
  *
  * Demonstrate streaming of pod logs, in this example the log is just a couple of short line printed to stdout
  */
object PodLogExample extends App {

  val printLogFlow: Sink[ByteString, NotUsed] = Flow[ByteString]
      .via(Framing.delimiter(
        ByteString("\n"),
        maximumFrameLength = 256,
        allowTruncation = true))
      .map(_.utf8String)
      .to(Sink.foreach(text => println(s"[hello-world logs] $text")))


  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher
  val k8s = client.init(
    client.defaultK8sConfig.currentContext,
    client.LoggingConfig(logRequestBasic = false, logResponseBasic = false) )

  val helloWorldContainer=Container(name="hello-world", image="busybox", command=List("sh", "-c", "echo Hello World! && echo Goodbye World && sleep 60"))
  val helloWorldPod=Pod("hello-world", Pod.Spec().addContainer(helloWorldContainer))

  val podFut = k8s.create(helloWorldPod)
  println("Waiting 30 seconds to allow pod initialisation to complete before getting logs...")
  Thread.sleep(30000)
  for {
    pod <- podFut
    logsSource <- k8s.getPodLogSource("hello-world", Pod.LogQueryParams())
    donePrinting = logsSource.runWith(printLogFlow)
  } yield donePrinting

  // allow another 5 seconds for logs to be streamed from the pod to stdout before cleaning up
  Thread.sleep(5000)
  Await.result(k8s.delete[Pod]("hello-world"), 5 seconds)
  k8s.close
  system.terminate
  System.exit(0)
}
