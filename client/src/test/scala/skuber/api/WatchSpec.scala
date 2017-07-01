package skuber.api

import client._
import skuber.json.format._
import skuber.ReplicationController

import org.specs2.mutable.Specification
import org.specs2.execute.Result
import org.specs2.execute.Failure
import org.specs2.execute.Success

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

import play.api.libs.iteratee.{Enumerator,Iteratee}

/**
 * @author David O'Riordan
 */
class WatchSpec extends Specification {
  "A single chunk containing a single Watch event can be enumerated correctly" >> {
    val eventsAsStr =  """{"type":"MODIFIED","object":{"kind":"ReplicationController","apiVersion":"v1","metadata":{"name":"frontend","namespace":"default","selfLink":"/api/v1/namespaces/default/replicationcontrollers/frontend","uid":"246f12b6-719b-11e5-89ae-0800279dd272","resourceVersion":"12803","generation":2,"creationTimestamp":"2015-10-13T11:11:24Z","labels":{"name":"frontend"}},"spec":{"replicas":0,"selector":{"name":"frontend"},"template":{"metadata":{"name":"frontend","namespace":"default","creationTimestamp":null,"labels":{"name":"frontend"}},"spec":{"containers":[{"name":"php-redis","image":"kubernetes/example-guestbook-php-redis:v2","ports":[{"containerPort":80,"protocol":"TCP"}],"resources":{},"terminationMessagePath":"/var/log/termination","imagePullPolicy":"IfNotPresent"}],"restartPolicy":"Always","dnsPolicy":"Default"}}},"status":{"replicas":3,"observedGeneration":2}}}"""
    val eventsAsBytes = eventsAsStr.getBytes
    val eventBytesEnumerator = Enumerator(eventsAsBytes)
    
    val events = Watch.fromBytesEnumerator[ReplicationController]("test", eventBytesEnumerator)
    
    val res = events |>>> Iteratee.getChunks
    
    val rcSeq = Await.result(res, Duration.Inf)
    
    rcSeq.length mustEqual 1
    rcSeq.head._object.name mustEqual "frontend"  
      
  }  
  
  "A single chunk containing two Watch events can be enumerated correctly" >> {
    val eventsAsStr =  """{"type":"MODIFIED","object":{"kind":"ReplicationController","apiVersion":"v1","metadata":{"name":"frontend","namespace":"default","selfLink":"/api/v1/namespaces/default/replicationcontrollers/frontend","uid":"246f12b6-719b-11e5-89ae-0800279dd272","resourceVersion":"12803","generation":2,"creationTimestamp":"2015-10-13T11:11:24Z","labels":{"name":"frontend"}},"spec":{"replicas":0,"selector":{"name":"frontend"},"template":{"metadata":{"name":"frontend","namespace":"default","creationTimestamp":null,"labels":{"name":"frontend"}},"spec":{"containers":[{"name":"php-redis","image":"kubernetes/example-guestbook-php-redis:v2","ports":[{"containerPort":80,"protocol":"TCP"}],"resources":{},"terminationMessagePath":"/var/log/termination","imagePullPolicy":"IfNotPresent"}],"restartPolicy":"Always","dnsPolicy":"Default"}}},"status":{"replicas":3,"observedGeneration":2}}}
{"type":"MODIFIED","object":{"kind":"ReplicationController","apiVersion":"v1","metadata":{"name":"frontend","namespace":"default","selfLink":"/api/v1/namespaces/default/replicationcontrollers/frontend","uid":"246f12b6-719b-11e5-89ae-0800279dd272","resourceVersion":"12804","generation":2,"creationTimestamp":"2015-10-13T11:11:24Z","labels":{"name":"frontend"}},"spec":{"replicas":0,"selector":{"name":"frontend"},"template":{"metadata":{"name":"frontend","namespace":"default","creationTimestamp":null,"labels":{"name":"frontend"}},"spec":{"containers":[{"name":"php-redis","image":"kubernetes/example-guestbook-php-redis:v2","ports":[{"containerPort":80,"protocol":"TCP"}],"resources":{},"terminationMessagePath":"/var/log/termination","imagePullPolicy":"IfNotPresent"}],"restartPolicy":"Always","dnsPolicy":"Default"}}},"status":{"replicas":0,"observedGeneration":2}}}
"""
    val eventsAsBytes = eventsAsStr.getBytes
    val eventBytesEnumerator = Enumerator(eventsAsBytes)
    
    val events = Watch.fromBytesEnumerator[ReplicationController]("test", eventBytesEnumerator)
    
    val res = events |>>> Iteratee.getChunks
    
    val rcSeq = Await.result(res, Duration.Inf)
    
    rcSeq.length mustEqual 2
    rcSeq(0)._object.status.get.replicas mustEqual 3
    rcSeq(1)._object.status.get.replicas mustEqual 0      
  }  
  
   "Two chunks containing two Watch events with one split across the chunks can be enumerated correctly" >> {
    val event1AsStr = """{"type":"MODIFIED","object":{"kind":"ReplicationController","apiVersion":"v1","metadata":{"name":"frontend","namespace":"default","selfLink":"/api/v1/namespaces/default/replicationcontrollers/frontend","uid":"246f12b6-719b-11e5-89ae-0800279dd272","resourceVersion":"12803","generation":2,"creationTimestamp":"2015-10-13T11:11:24Z","labels":{"name":"frontend"}},"spec":{"replicas":0,"selector":{"name":"frontend"},"template":{"metadata":{"name":"frontend","namespace":"default","creationTimestamp":null,"labels":{"name":"frontend"}},"spec":{"containers":[{"name":"php-redis","image":"kubernetes/example-guestbook-php-redis:v2","ports":[{"containerPort":80,"protocol":"TCP"}],"resources":{},"terminationMessagePath":"/var/log/termination","imagePullPolicy":"IfNotPresent"}],"restartPolicy":"Always","dnsPolicy":"Default"}}},"status":{"replicas":3,"observedGeneration":2}}}
{"type":"MODIFIED","object":{"kind":"ReplicationController","apiVersion":"v1","metadata":{"name":"frontend","namespace":"default","selfLink":"/api/v1/namespaces/default/replicationcontrollers/frontend","uid":"246f12b6-719b-11e5-89ae-0800279dd272","resourceVersion":"12804","generation":2,"creationTimestamp":"2015-10-13T11:11:2"""
    val event2AsStr = """4Z","labels":{"name":"frontend"}},"spec":{"replicas":0,"selector":{"name":"frontend"},"template":{"metadata":{"name":"frontend","namespace":"default","creationTimestamp":null,"labels":{"name":"frontend"}},"spec":{"containers":[{"name":"php-redis","image":"kubernetes/example-guestbook-php-redis:v2","ports":[{"containerPort":80,"protocol":"TCP"}],"resources":{},"terminationMessagePath":"/var/log/termination","imagePullPolicy":"IfNotPresent"}],"restartPolicy":"Always","dnsPolicy":"Default"}}},"status":{"replicas":0,"observedGeneration":2}}}
"""
    val event1AsBytes = event1AsStr.getBytes
    val event2AsBytes = event2AsStr.getBytes
    val eventBytesEnumerator = Enumerator(event1AsBytes, event2AsBytes)
    
    val events = Watch.fromBytesEnumerator[ReplicationController]("test", eventBytesEnumerator)
    
    val res = events |>>> Iteratee.getChunks
    
    val rcSeq = Await.result(res, Duration.Inf)
    
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
    val events1AsBytes = events1AsStr.getBytes
    val events2AsBytes = events2AsStr.getBytes    
    val eventBytesEnumerator = Enumerator(events1AsBytes, events2AsBytes)
    
    val events = Watch.fromBytesEnumerator[ReplicationController]("test", eventBytesEnumerator)
    
    val res = events |>>> Iteratee.getChunks
    
    val rcSeq = Await.result(res, Duration.Inf)
    
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
    val event1AsBytes = event1AsStr.getBytes
    val event2AsBytes = event2AsStr.getBytes   
    val event3AsBytes = event3AsStr.getBytes
    val event4AsBytes = event4AsStr.getBytes  
    
    val eventBytesEnumerator = Enumerator(event1AsBytes, event2AsBytes,event3AsBytes,event4AsBytes)
    
    val events = Watch.fromBytesEnumerator[ReplicationController]("test", eventBytesEnumerator)
    
    val res = events |>>> Iteratee.getChunks
    val rcSeq = Await.result(res, Duration.Inf)
    
    rcSeq.length mustEqual 4
    rcSeq(0)._object.status.get.replicas mustEqual 3
    rcSeq(1)._object.status.get.replicas mustEqual 2
    rcSeq(2)._object.status.get.replicas mustEqual 1
    rcSeq(3)._object.status.get.replicas mustEqual 0
  }
}