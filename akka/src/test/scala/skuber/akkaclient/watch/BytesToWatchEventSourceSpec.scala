package skuber.akkaclient.watch

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import org.scalatestplus.mockito.MockitoSugar.mock
import org.specs2.mutable.Specification
import skuber.akkaclient.impl.AkkaKubernetesClientImpl
import skuber.api.client.{LoggingContext, WatchEvent}
import skuber.json.format._
import skuber.model.ReplicationController

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * @author David O'Riordan
  *
 */
class BytesToWatchEventSourceSpec extends Specification {

  implicit val system: ActorSystem = ActorSystem("test")
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val loggingContext: LoggingContext = new LoggingContext { override def output:String="test" }
  val client = mock[AkkaKubernetesClientImpl]

  "A single chunk containing a single Watch event can be read correctly" >> {
    val eventsAsStr =  """{"type":"MODIFIED","object":{"kind":"ReplicationController","apiVersion":"v1","metadata":{"name":"frontend","namespace":"default","selfLink":"/api/v1/namespaces/default/replicationcontrollers/frontend","uid":"246f12b6-719b-11e5-89ae-0800279dd272","resourceVersion":"12803","generation":2,"creationTimestamp":"2015-10-13T11:11:24Z","labels":{"name":"frontend"}},"spec":{"replicas":0,"selector":{"name":"frontend"},"template":{"metadata":{"name":"frontend","namespace":"default","creationTimestamp":null,"labels":{"name":"frontend"}},"spec":{"containers":[{"name":"php-redis","image":"kubernetes/example-guestbook-php-redis:v2","ports":[{"containerPort":80,"protocol":"TCP"}],"resources":{},"terminationMessagePath":"/var/log/termination","imagePullPolicy":"IfNotPresent"}],"restartPolicy":"Always","dnsPolicy":"Default"}}},"status":{"replicas":3,"observedGeneration":2}}}"""
    val bytesSource = Source.single(ByteString(eventsAsStr))
    val watchEventSource = BytesToWatchEventSource[ReplicationController](client, bytesSource, 1000, None)

    val eventSink = Sink.head[WatchEvent[ReplicationController]]
    val run: Future[WatchEvent[ReplicationController]] = watchEventSource.runWith(eventSink)

    val result = Await.result(run, Duration.Inf)
    result._object.name must beEqualTo("frontend")
  }
  
  "A single chunk containing two Watch events can be enumerated correctly" >> {
    val eventsAsStr =  """{"type":"MODIFIED","object":{"kind":"ReplicationController","apiVersion":"v1","metadata":{"name":"frontend","namespace":"default","selfLink":"/api/v1/namespaces/default/replicationcontrollers/frontend","uid":"246f12b6-719b-11e5-89ae-0800279dd272","resourceVersion":"12803","generation":2,"creationTimestamp":"2015-10-13T11:11:24Z","labels":{"name":"frontend"}},"spec":{"replicas":0,"selector":{"name":"frontend"},"template":{"metadata":{"name":"frontend","namespace":"default","creationTimestamp":null,"labels":{"name":"frontend"}},"spec":{"containers":[{"name":"php-redis","image":"kubernetes/example-guestbook-php-redis:v2","ports":[{"containerPort":80,"protocol":"TCP"}],"resources":{},"terminationMessagePath":"/var/log/termination","imagePullPolicy":"IfNotPresent"}],"restartPolicy":"Always","dnsPolicy":"Default"}}},"status":{"replicas":3,"observedGeneration":2}}}
{"type":"MODIFIED","object":{"kind":"ReplicationController","apiVersion":"v1","metadata":{"name":"frontend","namespace":"default","selfLink":"/api/v1/namespaces/default/replicationcontrollers/frontend","uid":"246f12b6-719b-11e5-89ae-0800279dd272","resourceVersion":"12804","generation":2,"creationTimestamp":"2015-10-13T11:11:24Z","labels":{"name":"frontend"}},"spec":{"replicas":0,"selector":{"name":"frontend"},"template":{"metadata":{"name":"frontend","namespace":"default","creationTimestamp":null,"labels":{"name":"frontend"}},"spec":{"containers":[{"name":"php-redis","image":"kubernetes/example-guestbook-php-redis:v2","ports":[{"containerPort":80,"protocol":"TCP"}],"resources":{},"terminationMessagePath":"/var/log/termination","imagePullPolicy":"IfNotPresent"}],"restartPolicy":"Always","dnsPolicy":"Default"}}},"status":{"replicas":0,"observedGeneration":2}}}
"""
    val bytesSource = Source.single(ByteString(eventsAsStr))
    val watchEventSource: Source[WatchEvent[ReplicationController], _] = BytesToWatchEventSource[ReplicationController](client, bytesSource, 1000, None)

    val eventSink = Sink.seq[WatchEvent[ReplicationController]]
    val run: Future[Seq[WatchEvent[ReplicationController]]] = watchEventSource.runWith(eventSink)

    val rcSeq = Await.result(run, Duration.Inf)

    rcSeq.length must beEqualTo(2)
    rcSeq(0)._object.status.get.replicas must beEqualTo(3)
    rcSeq(1)._object.status.get.replicas must beEqualTo(0)
  }  
  
   "Two chunks containing two Watch events with one split across the chunks can be enumerated correctly" >> {
      val event1AsStr = """{"type":"MODIFIED","object":{"kind":"ReplicationController","apiVersion":"v1","metadata":{"name":"frontend","namespace":"default","selfLink":"/api/v1/namespaces/default/replicationcontrollers/frontend","uid":"246f12b6-719b-11e5-89ae-0800279dd272","resourceVersion":"12803","generation":2,"creationTimestamp":"2015-10-13T11:11:24Z","labels":{"name":"frontend"}},"spec":{"replicas":0,"selector":{"name":"frontend"},"template":{"metadata":{"name":"frontend","namespace":"default","creationTimestamp":null,"labels":{"name":"frontend"}},"spec":{"containers":[{"name":"php-redis","image":"kubernetes/example-guestbook-php-redis:v2","ports":[{"containerPort":80,"protocol":"TCP"}],"resources":{},"terminationMessagePath":"/var/log/termination","imagePullPolicy":"IfNotPresent"}],"restartPolicy":"Always","dnsPolicy":"Default"}}},"status":{"replicas":3,"observedGeneration":2}}}
{"type":"MODIFIED","object":{"kind":"ReplicationController","apiVersion":"v1","metadata":{"name":"frontend","namespace":"default","selfLink":"/api/v1/namespaces/default/replicationcontrollers/frontend","uid":"246f12b6-719b-11e5-89ae-0800279dd272","resourceVersion":"12804","generation":2,"creationTimestamp":"2015-10-13T11:11:2"""
      val event2AsStr = """4Z","labels":{"name":"frontend"}},"spec":{"replicas":0,"selector":{"name":"frontend"},"template":{"metadata":{"name":"frontend","namespace":"default","creationTimestamp":null,"labels":{"name":"frontend"}},"spec":{"containers":[{"name":"php-redis","image":"kubernetes/example-guestbook-php-redis:v2","ports":[{"containerPort":80,"protocol":"TCP"}],"resources":{},"terminationMessagePath":"/var/log/termination","imagePullPolicy":"IfNotPresent"}],"restartPolicy":"Always","dnsPolicy":"Default"}}},"status":{"replicas":0,"observedGeneration":2}}}
"""
      val bytesSource = Source(List(ByteString(event1AsStr), ByteString(event2AsStr)))
      val watchEventSource = BytesToWatchEventSource[ReplicationController](client, bytesSource, 1000, None)

      val eventSink = Sink.seq[WatchEvent[ReplicationController]]
      val run: Future[Seq[WatchEvent[ReplicationController]]] = watchEventSource.runWith(eventSink)

      val rcSeq = Await.result(run, Duration.Inf)

      rcSeq.length must beEqualTo(2)
      rcSeq(0)._object.status.get.replicas must beEqualTo(3)
      rcSeq(1)._object.status.get.replicas must beEqualTo(0)
   } 
   
  "Two chunks containing four Watch events, with a middle event split across the chunks, can be enumerated correctly" >> {
      val events1AsStr =  """{"type":"MODIFIED","object":{"kind":"ReplicationController","apiVersion":"v1","metadata":{"name":"frontend","namespace":"default","selfLink":"/api/v1/namespaces/default/replicationcontrollers/frontend","uid":"246f12b6-719b-11e5-89ae-0800279dd272","resourceVersion":"12803","generation":2,"creationTimestamp":"2015-10-13T11:11:24Z","labels":{"name":"frontend"}},"spec":{"replicas":0,"selector":{"name":"frontend"},"template":{"metadata":{"name":"frontend","namespace":"default","creationTimestamp":null,"labels":{"name":"frontend"}},"spec":{"containers":[{"name":"php-redis","image":"kubernetes/example-guestbook-php-redis:v2","ports":[{"containerPort":80,"protocol":"TCP"}],"resources":{},"terminationMessagePath":"/var/log/termination","imagePullPolicy":"IfNotPresent"}],"restartPolicy":"Always","dnsPolicy":"Default"}}},"status":{"replicas":3,"observedGeneration":2}}}
{"type":"MODIFIED","object":{"kind":"ReplicationController","apiVersion":"v1","metadata":{"name":"frontend","namespace":"default","selfLink":"/api/v1/namespaces/default/replicationcontrollers/frontend","uid":"246f12b6-719b-11e5-89ae-0800279dd272","resourceVersion":"12804","generation":2,"creationTimestamp":"2015-10-13T11:11:24Z","labels":{"name":"frontend"}},"spec":{"replicas":0,"selector":{"name":"frontend"},"template":{"metadata":{"name":"frontend","namespace":"default","creationTimestamp":null,"labels":{"name":"frontend"}},"spec":{"containers":[{"name":"php-redis","image":"kubernetes/example-guestbook-php-redis:v2","ports":[{"containerPort":80,"protocol":"TCP"}],"resources":{},"terminationMessagePath":"/var/log/termination","imagePullPolicy":"IfNotPresent"}],"restartPolicy":"Always","dnsPolicy":"Default"}}},"status":{"replicas":2,"observedGeneration":2}}}
{"type":"MODIFIED","object":{"kind":"ReplicationController","apiVersion":"v1","metadata":{"name":"frontend","namespace":"default","selfLink"
"""
      val events2AsStr =  """:"/api/v1/namespaces/default/replicationcontrollers/frontend","uid":"246f12b6-719b-11e5-89ae-0800279dd272","resourceVersion":"12804","generation":2,"creationTimestamp":"2015-10-13T11:11:24Z","labels":{"name":"frontend"}},"spec":{"replicas":0,"selector":{"name":"frontend"},"template":{"metadata":{"name":"frontend","namespace":"default","creationTimestamp":null,"labels":{"name":"frontend"}},"spec":{"containers":[{"name":"php-redis","image":"kubernetes/example-guestbook-php-redis:v2","ports":[{"containerPort":80,"protocol":"TCP"}],"resources":{},"terminationMessagePath":"/var/log/termination","imagePullPolicy":"IfNotPresent"}],"restartPolicy":"Always","dnsPolicy":"Default"}}},"status":{"replicas":1,"observedGeneration":2}}}
{"type":"MODIFIED","object":{"kind":"ReplicationController","apiVersion":"v1","metadata":{"name":"frontend","namespace":"default","selfLink":"/api/v1/namespaces/default/replicationcontrollers/frontend","uid":"246f12b6-719b-11e5-89ae-0800279dd272","resourceVersion":"12803","generation":2,"creationTimestamp":"2015-10-13T11:11:24Z","labels":{"name":"frontend"}},"spec":{"replicas":0,"selector":{"name":"frontend"},"template":{"metadata":{"name":"frontend","namespace":"default","creationTimestamp":null,"labels":{"name":"frontend"}},"spec":{"containers":[{"name":"php-redis","image":"kubernetes/example-guestbook-php-redis:v2","ports":[{"containerPort":80,"protocol":"TCP"}],"resources":{},"terminationMessagePath":"/var/log/termination","imagePullPolicy":"IfNotPresent"}],"restartPolicy":"Always","dnsPolicy":"Default"}}},"status":{"replicas":0,"observedGeneration":2}}}
"""
      val bytesSource = Source(List(ByteString(events1AsStr), ByteString(events2AsStr)))
      val watchEventSource = BytesToWatchEventSource[ReplicationController](client, bytesSource, 1000, None)

      val eventSink = Sink.seq[WatchEvent[ReplicationController]]
      val run: Future[Seq[WatchEvent[ReplicationController]]] = watchEventSource.runWith(eventSink)

      val rcSeq = Await.result(run, Duration.Inf)

      rcSeq.length must beEqualTo(4)
      rcSeq(0)._object.status.get.replicas must beEqualTo(3)
      rcSeq(1)._object.status.get.replicas must beEqualTo(2)
      rcSeq(2)._object.status.get.replicas must beEqualTo(1)
      rcSeq(3)._object.status.get.replicas must beEqualTo(0)
  } 
  
  "Four chunks, each containing a Watch event terminated by a line feed, can be enumerated" >> {
    val event1AsStr =  """{"type":"MODIFIED","object":{"kind":"ReplicationController","apiVersion":"v1","metadata":{"name":"frontend","namespace":"default","selfLink":"/api/v1/namespaces/default/replicationcontrollers/frontend","uid":"246f12b6-719b-11e5-89ae-0800279dd272","resourceVersion":"12803","generation":2,"creationTimestamp":"2015-10-13T11:11:24Z","labels":{"name":"frontend"}},"spec":{"replicas":0,"selector":{"name":"frontend"},"template":{"metadata":{"name":"frontend","namespace":"default","creationTimestamp":null,"labels":{"name":"frontend"}},"spec":{"containers":[{"name":"php-redis","image":"kubernetes/example-guestbook-php-redis:v2","ports":[{"containerPort":80,"protocol":"TCP"}],"resources":{},"terminationMessagePath":"/var/log/termination","imagePullPolicy":"IfNotPresent"}],"restartPolicy":"Always","dnsPolicy":"Default"}}},"status":{"replicas":3,"observedGeneration":2}}}
"""
    val event2AsStr = """{"type":"MODIFIED","object":{"kind":"ReplicationController","apiVersion":"v1","metadata":{"name":"frontend","namespace":"default","selfLink":"/api/v1/namespaces/default/replicationcontrollers/frontend","uid":"246f12b6-719b-11e5-89ae-0800279dd272","resourceVersion":"12804","generation":2,"creationTimestamp":"2015-10-13T11:11:24Z","labels":{"name":"frontend"}},"spec":{"replicas":0,"selector":{"name":"frontend"},"template":{"metadata":{"name":"frontend","namespace":"default","creationTimestamp":null,"labels":{"name":"frontend"}},"spec":{"containers":[{"name":"php-redis","image":"kubernetes/example-guestbook-php-redis:v2","ports":[{"containerPort":80,"protocol":"TCP"}],"resources":{},"terminationMessagePath":"/var/log/termination","imagePullPolicy":"IfNotPresent"}],"restartPolicy":"Always","dnsPolicy":"Default"}}},"status":{"replicas":2,"observedGeneration":2}}}
"""
    val event3AsStr = """{"type":"MODIFIED","object":{"kind":"ReplicationController","apiVersion":"v1","metadata":{"name":"frontend","namespace":"default","selfLink":"/api/v1/namespaces/default/replicationcontrollers/frontend","uid":"246f12b6-719b-11e5-89ae-0800279dd272","resourceVersion":"12804","generation":2,"creationTimestamp":"2015-10-13T11:11:24Z","labels":{"name":"frontend"}},"spec":{"replicas":0,"selector":{"name":"frontend"},"template":{"metadata":{"name":"frontend","namespace":"default","creationTimestamp":null,"labels":{"name":"frontend"}},"spec":{"containers":[{"name":"php-redis","image":"kubernetes/example-guestbook-php-redis:v2","ports":[{"containerPort":80,"protocol":"TCP"}],"resources":{},"terminationMessagePath":"/var/log/termination","imagePullPolicy":"IfNotPresent"}],"restartPolicy":"Always","dnsPolicy":"Default"}}},"status":{"replicas":1,"observedGeneration":2}}}
"""
    val event4AsStr = """{"type":"MODIFIED","object":{"kind":"ReplicationController","apiVersion":"v1","metadata":{"name":"frontend","namespace":"default","selfLink":"/api/v1/namespaces/default/replicationcontrollers/frontend","uid":"246f12b6-719b-11e5-89ae-0800279dd272","resourceVersion":"12803","generation":2,"creationTimestamp":"2015-10-13T11:11:24Z","labels":{"name":"frontend"}},"spec":{"replicas":0,"selector":{"name":"frontend"},"template":{"metadata":{"name":"frontend","namespace":"default","creationTimestamp":null,"labels":{"name":"frontend"}},"spec":{"containers":[{"name":"php-redis","image":"kubernetes/example-guestbook-php-redis:v2","ports":[{"containerPort":80,"protocol":"TCP"}],"resources":{},"terminationMessagePath":"/var/log/termination","imagePullPolicy":"IfNotPresent"}],"restartPolicy":"Always","dnsPolicy":"Default"}}},"status":{"replicas":0,"observedGeneration":2}}}
"""
    val bytesSource = Source(List(ByteString(event1AsStr), ByteString(event2AsStr), ByteString(event3AsStr), ByteString(event4AsStr)))
    val watchEventSource = BytesToWatchEventSource[ReplicationController](client, bytesSource, 1000, None)

    val eventSink = Sink.seq[WatchEvent[ReplicationController]]
    val run: Future[Seq[WatchEvent[ReplicationController]]] = watchEventSource.runWith(eventSink)

    val rcSeq = Await.result(run, Duration.Inf)

    rcSeq.length must beEqualTo(4)
    rcSeq(0)._object.status.get.replicas must beEqualTo(3)
    rcSeq(1)._object.status.get.replicas must beEqualTo(2)
    rcSeq(2)._object.status.get.replicas must beEqualTo(1)
    rcSeq(3)._object.status.get.replicas must beEqualTo(0)
  }

  "Four chunks, containing two ERROR events in the middle, can be correctly enumerated" >> {
    // normally the objects in error events are Status kinds
    val errorObject1Str = """{"kind":"Status","apiVersion":"v1","metadata":{},"status":"Failure","message":"some sort of error","reason":"who knows","code":410}"""
    val errorObject2Str = """{"kind":"Status","apiVersion":"v1","metadata":{},"status":"Failure","message":"some other sort of error","reason":"who knows","code":403}"""

    val event1AsStr =
      """{"type":"MODIFIED","object":{"kind":"ReplicationController","apiVersion":"v1","metadata":{"name":"frontend","namespace":"default","selfLink":"/api/v1/namespaces/default/replicationcontrollers/frontend","uid":"246f12b6-719b-11e5-89ae-0800279dd272","resourceVersion":"12803","generation":2,"creationTimestamp":"2015-10-13T11:11:24Z","labels":{"name":"frontend"}},"spec":{"replicas":0,"selector":{"name":"frontend"},"template":{"metadata":{"name":"frontend","namespace":"default","creationTimestamp":null,"labels":{"name":"frontend"}},"spec":{"containers":[{"name":"php-redis","image":"kubernetes/example-guestbook-php-redis:v2","ports":[{"containerPort":80,"protocol":"TCP"}],"resources":{},"terminationMessagePath":"/var/log/termination","imagePullPolicy":"IfNotPresent"}],"restartPolicy":"Always","dnsPolicy":"Default"}}},"status":{"replicas":3,"observedGeneration":2}}}
"""
    val event2AsStr =
      s"""{"type":"ERROR","object":$errorObject1Str}"""
    val event3AsStr =
      s"""{"type":"ERROR","object":$errorObject2Str}"""
    val event4AsStr =
      """{"type":"MODIFIED","object":{"kind":"ReplicationController","apiVersion":"v1","metadata":{"name":"frontend","namespace":"default","selfLink":"/api/v1/namespaces/default/replicationcontrollers/frontend","uid":"246f12b6-719b-11e5-89ae-0800279dd272","resourceVersion":"12803","generation":2,"creationTimestamp":"2015-10-13T11:11:24Z","labels":{"name":"frontend"}},"spec":{"replicas":0,"selector":{"name":"frontend"},"template":{"metadata":{"name":"frontend","namespace":"default","creationTimestamp":null,"labels":{"name":"frontend"}},"spec":{"containers":[{"name":"php-redis","image":"kubernetes/example-guestbook-php-redis:v2","ports":[{"containerPort":80,"protocol":"TCP"}],"resources":{},"terminationMessagePath":"/var/log/termination","imagePullPolicy":"IfNotPresent"}],"restartPolicy":"Always","dnsPolicy":"Default"}}},"status":{"replicas":0,"observedGeneration":2}}}
"""
    val errors = mutable.ListBuffer[String]()
    val errorHandler = (error: String) => errors.addOne(error)
    val bytesSource = Source(List(ByteString(event1AsStr), ByteString(event2AsStr), ByteString(event3AsStr), ByteString(event4AsStr)))
    val watchEventSource = BytesToWatchEventSource[ReplicationController](client, bytesSource, 1000, Some(errorHandler))

    val eventSink = Sink.seq[WatchEvent[ReplicationController]]
    val run: Future[Seq[WatchEvent[ReplicationController]]] = watchEventSource.runWith(eventSink)

    val rcSeq = Await.result(run, Duration.Inf)

    rcSeq.length must beEqualTo(2)
    rcSeq(0)._object.status.get.replicas must beEqualTo(3)
    rcSeq(1)._object.status.get.replicas must beEqualTo(0)

    errors.length must beEqualTo(2)
    errors(0) must beEqualTo(errorObject1Str)
    errors(1) must beEqualTo(errorObject2Str)
  }
}