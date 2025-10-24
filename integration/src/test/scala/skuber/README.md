# Integration Tests Structure

This package contains the logic for most of the integration tests.

It consists of a base fixture class (that defines a base Kubernetes client as the main fixture), and a set of integration tests for specific areas of functionality.

Each test class here is an abstract base class that is extended by equivalent concrete classes in the Akka and Pekko modules, which mixin the concrete client to be used. This enables the bulk of the integration test code to be implemented in this package but be shared across both types of Kubernetes client, while allowing the specific Kubernetes client to be tested to be selected when running the tests.

These client-independent base classes can't implement tests for functionality that requires the code to be using specific Akka or Pekko concrete client methods (for example watchers and pod logging/execution tests). Those tests are implemented in the respective Akka/Pekko integration test concrete classes instead.
