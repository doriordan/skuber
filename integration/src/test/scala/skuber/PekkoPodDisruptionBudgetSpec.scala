package skuber

/**
 * Pekko-specific concrete implementation of PodDisruptionBudgetSpec integration tests.
 */
class PekkoPodDisruptionBudgetSpec extends PodDisruptionBudgetSpec with PekkoK8SFixture