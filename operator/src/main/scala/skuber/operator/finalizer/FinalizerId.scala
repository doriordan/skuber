package skuber.operator.finalizer

/**
 * Finalizer identifier for an operator.
 *
 * By convention, finalizers should be in the format `domain/name`,
 * e.g., `mycompany.com/my-operator`.
 *
 * @param value The full finalizer string
 */
case class FinalizerId(value: String):
  require(
    value.contains("/") || value.contains("."),
    "Finalizer ID should be in format 'domain/name' or contain a domain"
  )

object FinalizerId:
  /**
   * Create a finalizer ID from domain and name.
   *
   * @param domain The domain (e.g., "mycompany.com")
   * @param name The finalizer name (e.g., "cleanup")
   */
  def apply(domain: String, name: String): FinalizerId =
    FinalizerId(s"$domain/$name")
