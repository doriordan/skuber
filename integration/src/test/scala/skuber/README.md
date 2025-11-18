# Integration Tests Structure

This package contains all of the integration tests. Their purpose is to test Skuber functionality against actual Kubernetes clusters, but they also provide some useful examples of how to use different Skuber features.

Each test class here is an abstract base class that is extended by equivalent concrete classes for each of Pekko and Akka, which also inherit the concrete Kubernetes client to be used from the required test fixture class e.g.

`class PekkoServiceSpec extends ServiceSpec with PekkoK8SFixture`

In this case the concrete test class `PekkoServiceSpec` doesn't directly implement any tests, it instead just inherits all the test specs from `ServiceSpec` and simply mixes in `PekkoK8SFixture` to ensure the Pekko based Skuber client is used when this test class is run.

This enables the bulk of the integration test code to be implemented once in this package, but be run for either or both types of Kubernetes client by specifying the name(s) when running the test(s), for example:

```sbt
> sbt
[info] welcome to sbt 1.11.7 (Eclipse Adoptium Java 17.0.8.1)
...
sbt:root> integration / testOnly *Pekko*`
```

Make sure KUBECONFIG points to a valid cluster config for a running cluster (`kind` clusters are recommended for local development testing, but any working cluster should be fine) when running the tests.
