package skuber

/**
 * Akka-specific concrete implementation of PodDisruptionBudgetSpec integration tests.
 */
class AkkaPodDisruptionBudgetSpec extends PodDisruptionBudgetSpec with AkkaK8SFixture