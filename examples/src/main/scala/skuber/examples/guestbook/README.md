
# Reactive Guestbook Example

This example shows how to build a multi-tier web application using the Skuber API. It is based on the standard Kubernetes Guestbook example (see [here](https://github.com/kubernetes/kubernetes/tree/master/examples/guestbook)). However in addition to automating the steps described in that example, it also uses the Skuber reactive watch API to monitor the scaling up and down of the pods during the deployment of the application to Kubernetes, so that it only progresses to the next deployment step when the expected number of replicas are running.

It carries out four main steps, each of which logs its results out to standard output:

- *stopping* the Guestbook services if they are already running (by specifying replica counts of 0)
- *housekeeping* the Guestbook application (i.e removing the resources from Kubernetes if they exist)
- *(re-)creating* the Guestbook application on Kubernetes
- *validating* that all replicas are running (it does this by placing a watch on the replication controller) 

## Prerequisites

- A Kubernetes cluster must be running. 
- Ensure client is configured for that cluster - by default all you need is a `kubectl proxy` running locally on its default port 8001.

## Running the example

This is part of the 'examples' SBT sub-project. You can run it directly from sbt (you will need to choose it when asked) or use the [assembly task](https://github.com/sbt/sbt-assembly) to build a fat JAR with all its dependencies included in the JAR so that it can than be run using a simple `java -jar` command.

- Run sbt at the root project level 
- Select the examples project
- Run the `assembly` command to build the fat jar
- Run the example using `java -jar <JAR file>`

```console
$ sbt
> project examples
[info] Set current project to skuber-examples (in build file:..../skuber/) 
> assembly
.... (output from build processing) ....
[info] Packaging ..../skuber/examples/target/scala-2.11/skuber-examples-assembly-0.1.0.jar ...
[info] Done packaging.
[success] Total time: ......
> exit
$ java -jar examples/target/scala-2.11/skuber-examples-assembly-0.1.0.jar 
```

## Expected Output
 
If the deployment is successful then you will see output that looks something like:

```console
Deploying Guestbook application to Kubernetes.
This involves four steps:
=> stopping the Guestbook services if they are running (by specifying replica counts of 0)
=> housekeeping the Guestbook application (i.e. removing the resources from Kubernetes if they exist)
=> (re)creating the Guestbook application on Kubernetes
=> validating that all replicas are running

*** Now stopping services (if they already exist and are running)

  'frontend' => updating specified replica count on Kubernetes to 0
  'frontend' => 3 replicas currently running (target: 0)
  'frontend' => scaling in progress on Kubernetes - creating a reactive watch to monitor progress
  'frontend' => 3 replicas currently running (target: 0)
  'frontend' => 0 replicas currently running (target: 0)
  'frontend' => successfully stopped all replica(s)
Front-end service stopped
  'redis-slave' => updating specified replica count on Kubernetes to 0
  'redis-slave' => 2 replicas currently running (target: 0)
  'redis-slave' => scaling in progress on Kubernetes - creating a reactive watch to monitor progress
  'redis-slave' => 2 replicas currently running (target: 0)
  'redis-slave' => 0 replicas currently running (target: 0)
  'redis-slave' => successfully stopped all replica(s)
Redis slave service stopped
  'redis-master' => updating specified replica count on Kubernetes to 0
  'redis-master' => 1 replicas currently running (target: 0)
  'redis-master' => scaling in progress on Kubernetes - creating a reactive watch to monitor progress
  'redis-master' => 1 replicas currently running (target: 0)
  'redis-master' => 0 replicas currently running (target: 0)
  'redis-master' => successfully stopped all replica(s)
Redis master service stopped

*** Now removing previous deployment (if necessary)

Front-end service & replication controller from previous deployment(s) have been removed (if they existed)
Redis slave service & replication controller from previous deployment(s) have been removed (if they existed)
Redis master service & replication controller from previous deployment(s) removed (if they existed)

*** Now (re)creating the services and replication controllers on Kubernetes

Front-end service & replication controller (re)created
Redis slave service & replication controller (re)created
Redis master service & replication controller (re)created

*** Now validating that all replicas are running - if required reactively watch status until done

  'redis-master' => 1 replicas currently running (target: 1)
  'redis-master' => successfully scaled to 1 replica(s)
All Redis master replicas are now running
  'redis-slave' => 0 replicas currently running (target: 2)
  'redis-slave' => scaling in progress on Kubernetes - creating a reactive watch to monitor progress
  'redis-slave' => 2 replicas currently running (target: 2)
  'redis-slave' => successfully scaled to 2 replica(s)
All Redis slave replicas are now running
  'frontend' => 0 replicas currently running (target: 3)
  'frontend' => scaling in progress on Kubernetes - creating a reactive watch to monitor progress
  'frontend' => 3 replicas currently running (target: 3)
  'frontend' => successfully scaled to 3 replica(s)
All front-end replicas are now running

*** Deployment of Guestbook application to Kubernetes completed successfully!
```

*Note: If you don't already have the guestbook deployed (normally just the first time you run the example) then you will see the Skuber API log some 404 errors because it doesn't find the requested resources for the first steps of stopping/housekeeping them. This is expected and handled by the example.* 

You can verify the Guestbook application is available by navigating to a service URL in your browser: because the example guestbook service specifies a [NodePort](https://github.com/kubernetes/kubernetes/blob/master/docs/user-guide/services.md#type-nodeport) service type with port 30291, your URL can use the host / IP address of any cluster node (obtained using e.g. `kubectl get nodes`). For example, if there is a node at 10.245.1.3 then navigating to `http://10.245.1.3:30291` should display the main Guestbook UI page. You can try this for each node in your cluster. 

## Design

The design of the reactive guestbook example is based on the [actor model](https://en.wikipedia.org/wiki/Actor_model) and uses [Pekko](https://pekko.apache.org/what-is-pekko.html). Note that Skuber itself has no dependency on Pekko - an actor model was chosen for this example simply because it seemed an appropriate abstraction, especially due to the asynchronous and reactive nature of the processing. The actors in this example run purely locally.

There are four actors involved in the example:

- `GuestbookActor` - this is the main actor responsible for orchestrating the high-level steps required to deploy the entire application, by creating child actors that do the actual work.
- `KubernetesProxy` - this is a singleton actor which proxies requests to Kubernetes (via the Skuber API) from the other actors. For the most part it is a very thin proxy - each message it can receive wraps the data for the request, and the response is simply piped back in another message to the sender. It also supports managing reactive watches i.e. if it receives a message asking to set a watch on a specific controller (generally Scaler actors ask for this) it will create the watch so that any updates to the watched object are sent back as messages to the requesting actor.
- `ServiceActor` - this manages deploying the service and replication controller for a given Guestbook service (the application contains three such services)
- `ScalerActor` - this actor manages scaling of a specified service, by updating the specified replica count and (if necessary) setting a reactive watch until it is notified that the count has been reached. It then sends a ScalingDone message back to the requesting actor.  
