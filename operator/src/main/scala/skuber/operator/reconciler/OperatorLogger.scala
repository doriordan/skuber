package skuber.operator.reconciler

import org.slf4j.{Logger, LoggerFactory}

/**
 * Structured logger for operator reconciliation.
 * Automatically includes resource context in log messages.
 */
trait OperatorLogger:
  def debug(msg: => String): Unit
  def info(msg: => String): Unit
  def warn(msg: => String): Unit
  def error(msg: => String): Unit
  def error(msg: => String, cause: Throwable): Unit

  /**
   * Create a child logger with additional context.
   */
  def withValues(values: (String, String)*): OperatorLogger

/**
 * SLF4J-based implementation of OperatorLogger.
 */
class Slf4jOperatorLogger(
  name: String,
  context: Map[String, String] = Map.empty
) extends OperatorLogger:

  private val underlying: Logger = LoggerFactory.getLogger(name)

  private def format(msg: String): String =
    if context.isEmpty then msg
    else
      val contextStr = context.map { case (k, v) => s"$k=$v" }.mkString(" ")
      s"[$contextStr] $msg"

  def debug(msg: => String): Unit =
    if underlying.isDebugEnabled then underlying.debug(format(msg))

  def info(msg: => String): Unit =
    if underlying.isInfoEnabled then underlying.info(format(msg))

  def warn(msg: => String): Unit =
    if underlying.isWarnEnabled then underlying.warn(format(msg))

  def error(msg: => String): Unit =
    if underlying.isErrorEnabled then underlying.error(format(msg))

  def error(msg: => String, cause: Throwable): Unit =
    if underlying.isErrorEnabled then underlying.error(format(msg), cause)

  def withValues(values: (String, String)*): OperatorLogger =
    new Slf4jOperatorLogger(name, context ++ values.toMap)

object OperatorLogger:
  def apply(name: String): OperatorLogger = new Slf4jOperatorLogger(name)

  def apply(clazz: Class[?]): OperatorLogger = new Slf4jOperatorLogger(clazz.getName)

  def forResource(kind: String, namespace: String, name: String): OperatorLogger =
    new Slf4jOperatorLogger(
      s"skuber.operator.$kind",
      Map("namespace" -> namespace, "name" -> name)
    )
