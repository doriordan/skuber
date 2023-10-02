package skuber.api

import client._
import skuber.json.format._
import skuber.ReplicationController
import org.specs2.mutable.Specification
import org.apache.pekko.util.ByteString
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.actor.ActorSystem
import skuber.api.watch.BytesToWatchEventSource

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration

/**
 * @author David O'Riordan
  *
 */
class BytesToWatchEventSourceSpec extends Specification {

  implicit val system: ActorSystem = ActorSystem("test")
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val loggingContext: LoggingContext = new LoggingContext { override def output:String="test" }

  "A single chunk containing a single Watch event can be read correctly" >> {
    val eventsAsStr =  """{"type":"MODIFIED","object":{"kind":"ReplicationController","apiVersion":"v1","metadata":{"name":"frontend","namespace":"default","selfLink":"/api/v1/namespaces/default/replicationcontrollers/frontend","uid":"246f12b6-719b-11e5-89ae-0800279dd272","resourceVersion":"12803","generation":2,"creationTimestamp":"2015-10-13T11:11:24Z","labels":{"name":"frontend"}},"spec":{"replicas":0,"selector":{"name":"frontend"},"template":{"metadata":{"name":"frontend","namespace":"default","creationTimestamp":null,"labels":{"name":"frontend"}},"spec":{"containers":[{"name":"php-redis","image":"kubernetes/example-guestbook-php-redis:v2","ports":[{"containerPort":80,"protocol":"TCP"}],"resources":{},"terminationMessagePath":"/var/log/termination","imagePullPolicy":"IfNotPresent"}],"restartPolicy":"Always","dnsPolicy":"Default"}}},"status":{"replicas":3,"observedGeneration":2}}}"""
    val bytesSource = Source.single(ByteString(eventsAsStr))
    val watchEventSource = BytesToWatchEventSource[ReplicationController](bytesSource, 1000)

    val eventSink = Sink.head[WatchEvent[ReplicationController]]
    val run: Future[WatchEvent[ReplicationController]] = watchEventSource.runWith(eventSink)

    val result = Await.result(run, Duration.Inf)
    result._object.name mustEqual "frontend"
  }  
  
  "A single chunk containing two Watch events can be enumerated correctly" >> {
    val eventsAsStr =  """{"type":"MODIFIED","object":{"kind":"ReplicationController","apiVersion":"v1","metadata":{"name":"frontend","namespace":"default","selfLink":"/api/v1/namespaces/default/replicationcontrollers/frontend","uid":"246f12b6-719b-11e5-89ae-0800279dd272","resourceVersion":"12803","generation":2,"creationTimestamp":"2015-10-13T11:11:24Z","labels":{"name":"frontend"}},"spec":{"replicas":0,"selector":{"name":"frontend"},"template":{"metadata":{"name":"frontend","namespace":"default","creationTimestamp":null,"labels":{"name":"frontend"}},"spec":{"containers":[{"name":"php-redis","image":"kubernetes/example-guestbook-php-redis:v2","ports":[{"containerPort":80,"protocol":"TCP"}],"resources":{},"terminationMessagePath":"/var/log/termination","imagePullPolicy":"IfNotPresent"}],"restartPolicy":"Always","dnsPolicy":"Default"}}},"status":{"replicas":3,"observedGeneration":2}}}
{"type":"MODIFIED","object":{"kind":"ReplicationController","apiVersion":"v1","metadata":{"name":"frontend","namespace":"default","selfLink":"/api/v1/namespaces/default/replicationcontrollers/frontend","uid":"246f12b6-719b-11e5-89ae-0800279dd272","resourceVersion":"12804","generation":2,"creationTimestamp":"2015-10-13T11:11:24Z","labels":{"name":"frontend"}},"spec":{"replicas":0,"selector":{"name":"frontend"},"template":{"metadata":{"name":"frontend","namespace":"default","creationTimestamp":null,"labels":{"name":"frontend"}},"spec":{"containers":[{"name":"php-redis","image":"kubernetes/example-guestbook-php-redis:v2","ports":[{"containerPort":80,"protocol":"TCP"}],"resources":{},"terminationMessagePath":"/var/log/termination","imagePullPolicy":"IfNotPresent"}],"restartPolicy":"Always","dnsPolicy":"Default"}}},"status":{"replicas":0,"observedGeneration":2}}}
"""
    val bytesSource = Source.single(ByteString(eventsAsStr))
    val watchEventSource: Source[WatchEvent[ReplicationController], _] = BytesToWatchEventSource[ReplicationController](bytesSource, 1000)

    val eventSink = Sink.seq[WatchEvent[ReplicationController]]
    val run: Future[Seq[WatchEvent[ReplicationController]]] = watchEventSource.runWith(eventSink)

    val rcSeq = Await.result(run, Duration.Inf)
    
    rcSeq.length mustEqual 2
    rcSeq(0)._object.status.get.replicas mustEqual 3
    rcSeq(1)._object.status.get.replicas mustEqual 0      
  }  
  
   "Two chunks containing two Watch events with one split across the chunks can be enumerated correctly" >> {
      val event1AsStr = """{"type":"MODIFIED","object":{"kind":"ReplicationController","apiVersion":"v1","metadata":{"name":"frontend","namespace":"default","selfLink":"/api/v1/namespaces/default/replicationcontrollers/frontend","uid":"246f12b6-719b-11e5-89ae-0800279dd272","resourceVersion":"12803","generation":2,"creationTimestamp":"2015-10-13T11:11:24Z","labels":{"name":"frontend"}},"spec":{"replicas":0,"selector":{"name":"frontend"},"template":{"metadata":{"name":"frontend","namespace":"default","creationTimestamp":null,"labels":{"name":"frontend"}},"spec":{"containers":[{"name":"php-redis","image":"kubernetes/example-guestbook-php-redis:v2","ports":[{"containerPort":80,"protocol":"TCP"}],"resources":{},"terminationMessagePath":"/var/log/termination","imagePullPolicy":"IfNotPresent"}],"restartPolicy":"Always","dnsPolicy":"Default"}}},"status":{"replicas":3,"observedGeneration":2}}}
{"type":"MODIFIED","object":{"kind":"ReplicationController","apiVersion":"v1","metadata":{"name":"frontend","namespace":"default","selfLink":"/api/v1/namespaces/default/replicationcontrollers/frontend","uid":"246f12b6-719b-11e5-89ae-0800279dd272","resourceVersion":"12804","generation":2,"creationTimestamp":"2015-10-13T11:11:2"""
      val event2AsStr = """4Z","labels":{"name":"frontend"}},"spec":{"replicas":0,"selector":{"name":"frontend"},"template":{"metadata":{"name":"frontend","namespace":"default","creationTimestamp":null,"labels":{"name":"frontend"}},"spec":{"containers":[{"name":"php-redis","image":"kubernetes/example-guestbook-php-redis:v2","ports":[{"containerPort":80,"protocol":"TCP"}],"resources":{},"terminationMessagePath":"/var/log/termination","imagePullPolicy":"IfNotPresent"}],"restartPolicy":"Always","dnsPolicy":"Default"}}},"status":{"replicas":0,"observedGeneration":2}}}
"""
      val bytesSource = Source(List(ByteString(event1AsStr), ByteString(event2AsStr)))
      val watchEventSource = BytesToWatchEventSource[ReplicationController](bytesSource, 1000)

      val eventSink = Sink.seq[WatchEvent[ReplicationController]]
      val run: Future[Seq[WatchEvent[ReplicationController]]] = watchEventSource.runWith(eventSink)

      val rcSeq = Await.result(run, Duration.Inf)
    
      rcSeq.length mustEqual 2
      rcSeq(0)._object.status.get.replicas mustEqual 3
      rcSeq(1)._object.status.get.replicas mustEqual 0
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
      val watchEventSource = BytesToWatchEventSource[ReplicationController](bytesSource, 1000)

      val eventSink = Sink.seq[WatchEvent[ReplicationController]]
      val run: Future[Seq[WatchEvent[ReplicationController]]] = watchEventSource.runWith(eventSink)

      val rcSeq = Await.result(run, Duration.Inf)
    
      rcSeq.length mustEqual 4
      rcSeq(0)._object.status.get.replicas mustEqual 3
      rcSeq(1)._object.status.get.replicas mustEqual 2
      rcSeq(2)._object.status.get.replicas mustEqual 1
      rcSeq(3)._object.status.get.replicas mustEqual 0
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
    val watchEventSource = BytesToWatchEventSource[ReplicationController](bytesSource, 1000)

    val eventSink = Sink.seq[WatchEvent[ReplicationController]]
    val run: Future[Seq[WatchEvent[ReplicationController]]] = watchEventSource.runWith(eventSink)

    val rcSeq = Await.result(run, Duration.Inf)
    
    rcSeq.length mustEqual 4
    rcSeq(0)._object.status.get.replicas mustEqual 3
    rcSeq(1)._object.status.get.replicas mustEqual 2
    rcSeq(2)._object.status.get.replicas mustEqual 1
    rcSeq(3)._object.status.get.replicas mustEqual 0
  }
}
