# Migrating to Skuber release 2

The API for Skuber release 2.0 is largely compatible with release 1.x, but a few straightforward-looking changes will be required by 1.x clients as follows.

## Initialisation

Because skuber now uses [Akka Http](https://doc.akka.io/docs/akka-http/current/scala/http/) as its underlying HTTP client (instead of `Play WS`), the initialisation call to Skuber requires an implicit Akka `ActorSystem` and `ActorMaterializer` to be available:

```

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer



implicit val system = ActorSystem()
implicit val materializer = ActorMaterializer()

val k8s = skuber.k8sInit

```

The advantage of this change is flexibility - the client now has more control over the http client used by Skuber, through configuring the actor system and managing its lifecycle explicitly. For example, this now allows multiple Skuber request contexts to be created by the application (for example to target different namespaces on the same cluster) while still sharing the same actor system and therefore potentially the same underlying connection pool.

## Termination

As a consequence of the above, the Skuber `close()` call no longer closes any connection resources, it simply stops any future requests being made. Management of connection resources is now done by the implicitly passed actor system which is under control of the application, and it is thus an application responsibility to ensure the actor system termination happens correctly and at the right time (i.e. after the application no longer needs to make Skuber requests).

## Watch API

The Watch API uses Akka streams instead of the (now deprecated in Play) enumeratees/iteratees used in Skuber 1.x. This means that Skuber 1.x code such as the following:

```
val frontendFetch = k8s get[ReplicationController] "frontend"

frontendFetch onSuccess { case frontend =>
  val frontendWatch = k8s watch frontend
  frontendWatch.events |>>> Iteratee.foreach { frontendEvent => println("Current frontend replicas: " + frontendEvent._object.status.get.replicas) }
}
```

can be migrated to 2.x using something like the following:

```
import akka.stream.scaladsl.Sink
val frontendReplicaCountMonitor = Sink.foreach[K8SWatchEvent[ReplicationController]] { frontendEvent =>
      println("Current frontend replicas: " + frontendEvent._object.status.get.replicas)
}

for {
  frontendRC <- k8s.get[ReplicationController]("frontend")
  frontendRCWatch <- k8s.watch(frontendRC)
  done <- frontendRCWatch.runWith(frontendReplicaCountMonitor)
} yield done
```

In general if you need to convert your existing Play iteratee/enumeratee code to Akka streams, there is a detailed [migration guide](https://www.playframework.com/documentation/2.6.x/StreamsMigration25#Migrating-Enumerators-to-Sources).

## List objects matching a label selector

Skuber 1.x has an overloaded `list` method that allows you to either pass no parameters - in which case all objects of the given type and in the current namespace will be returned - or pass in a `LabelSelector` for a more selective result.

The latter method has now been renamed so the `list` method is no longer overloaded. This means that if you are using `list` with a label selector you need to simply rename the method you are calling to `listSelected`.

## partiallyUpdate

The `partiallyUpdate` method has been removed, as it was undocumented and didn't work anyway. In the longer-term, proper `PATCH` support would be nice to have but was not available in 1.x and is not yet available in 2.0.

## Configuration

The default initialisation call `skuber.init()` now takes the value of the `KUBECONFIG` environment variable into account when determining the location of the `kubeconfig file`. `SKUBER_CONFIG` will still take precedence if set, but if not set then `KUBECONFIG` will be used, and if that is not set either then the default behaviour is now to try to load configuration from the default kubeconfig file location (`$HOME/.kube/config`).

This aligns default behaviour more closely with other Kubernetes clients, but is a change from the previous fallback of connecting to a `kubectl proxy` running on `localhost:8080`. To restore that fallback behaviour, simply change the `skuber.init()` call to `skuber.init(K8SConfiguration.useLocalProxyDefault))`.

Note that you can still set the `SKUBER_URL` environment variable to override the default behaviour and connect to a proxy running at the specified server address.

## Logging

Logging of API calls has been overhauled in Skuber 2.0. This does not require any application changes, although if you have downstream log-processing you may need to change some queries against the logs, as some of the text and log levels may have changed.

However the biggest change is that clients can now optionally exert much greater control over logging through two different but complementary mechanisms:

- Passing an implicit `LogContext` to each request.

`skuber.api.client.LogContext` is a new trait that implements an `output` method which will get called for every Skuber log event for that request, and its result (a String) will be included in the logged event. You can easily override the default (which outputs a unique Skuber-generated request ID) by implementing that simple trait and passing instances of your class as an implicit parameter to each request on the Skuber API that makes a remote call.

Use cases could include - for example - incuding your own application-generated request IDs in the Skuber logs for end-to-end request tracking.

- Fine-grained enabling of different log events.

A `skuber.api.client.LoggingConfig` object is now associated with each Skuber request context, which allows fine-grained enabling or disabling of different log events at INFO log level. This gives the user fine control over the level of detail in the logs.

A `LoggingConfig` object can be passed explicitly to the init() call, or alternatively different log events can be enabled by setting associated system parameters.

## Miscellaneous

- From version 2.0.1, the `template` field in a StatefulSet spec has been made mandatory so is no longer an `Option` type, this minor change was a fix to reflect the actual Kubernetes API rules for the field.







