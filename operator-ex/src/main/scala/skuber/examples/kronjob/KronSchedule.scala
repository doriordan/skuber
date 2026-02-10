package skuber.examples.kronjob

import java.time.{ZonedDateTime, ZoneId}
import java.time.temporal.ChronoUnit

/**
 * Simple cron schedule parser and calculator for use with KronJob example operator
 *
 * Supports standard 5-field cron format: minute hour day-of-month month day-of-week
 *
 * Examples:
 *  - `0 * * * *` = every hour at minute 0
 *  - `0/5 * * * *` = every 5 minutes (alternative: star-slash-5)
 *  - `0 0 * * *` = daily at midnight
 *
 * This is a simplified implementation for the example. In production, consider
 * using a full cron library like cron4s.
 */
case class KronSchedule(
  minute: KronField,
  hour: KronField,
  dayOfMonth: KronField,
  month: KronField,
  dayOfWeek: KronField
):
  /**
   * Calculate the next scheduled time after the given time.
   */
  def nextAfter(after: ZonedDateTime): ZonedDateTime =
    var candidate = after.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
    var iterations = 0
    val maxIterations = 366 * 24 * 60 // Max 1 year of minutes

    while iterations < maxIterations do
      iterations += 1

      // Check month
      if !month.matches(candidate.getMonthValue) then
        candidate = candidate.plusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0)
      // Check day of month
      else if !dayOfMonth.matches(candidate.getDayOfMonth) then
        candidate = candidate.plusDays(1).withHour(0).withMinute(0)
      // Check day of week (1=Monday to 7=Sunday in Java, but cron uses 0=Sunday to 6=Saturday)
      else if !dayOfWeek.matches(javaDayOfWeekToCron(candidate.getDayOfWeek.getValue)) then
        candidate = candidate.plusDays(1).withHour(0).withMinute(0)
      // Check hour
      else if !hour.matches(candidate.getHour) then
        candidate = candidate.plusHours(1).withMinute(0)
      // Check minute
      else if !minute.matches(candidate.getMinute) then
        candidate = candidate.plusMinutes(1)
      else
        return candidate

    throw new RuntimeException(s"Could not find next scheduled time within $maxIterations iterations")

  /**
   * Calculate the most recent scheduled time before or equal to the given time.
   */
  def mostRecentBefore(before: ZonedDateTime): Option[ZonedDateTime] =
    var candidate = before.truncatedTo(ChronoUnit.MINUTES)
    var iterations = 0
    val maxIterations = 366 * 24 * 60

    while iterations < maxIterations do
      iterations += 1

      if matches(candidate) then
        return Some(candidate)

      candidate = candidate.minusMinutes(1)

    None

  private def matches(time: ZonedDateTime): Boolean =
    minute.matches(time.getMinute) &&
    hour.matches(time.getHour) &&
    dayOfMonth.matches(time.getDayOfMonth) &&
    month.matches(time.getMonthValue) &&
    dayOfWeek.matches(javaDayOfWeekToCron(time.getDayOfWeek.getValue))

  // Java: 1=Monday to 7=Sunday, Cron: 0=Sunday to 6=Saturday
  private def javaDayOfWeekToCron(javaDow: Int): Int =
    if javaDow == 7 then 0 else javaDow

/**
 * A field in a cron expression.
 */
sealed trait KronField:
  def matches(value: Int): Boolean

object KronField:
  /** Matches any value */
  case object Any extends KronField:
    def matches(value: Int): Boolean = true

  /** Matches a specific value */
  case class Exact(n: Int) extends KronField:
    def matches(value: Int): Boolean = value == n

  /** Matches a range of values */
  case class Range(start: Int, end: Int) extends KronField:
    def matches(value: Int): Boolean = value >= start && value <= end

  /** Matches values at a step interval */
  case class Step(base: KronField, step: Int) extends KronField:
    def matches(value: Int): Boolean = base match
      case Any => value % step == 0
      case Exact(n) => value >= n && (value - n) % step == 0
      case Range(start, _) => value >= start && (value - start) % step == 0
      case _ => false

  /** Matches a list of values */
  case class List(values: scala.List[KronField]) extends KronField:
    def matches(value: Int): Boolean = values.exists(_.matches(value))

  /** Parse a single cron field */
  def parse(s: String, min: Int, max: Int): KronField =
    if s == "*" then
      Any
    else if s.contains("/") then
      val parts = s.split("/")
      val base = parse(parts(0), min, max)
      val step = parts(1).toInt
      Step(base, step)
    else if s.contains(",") then
      List(s.split(",").toList.map(parse(_, min, max)))
    else if s.contains("-") then
      val parts = s.split("-")
      Range(parts(0).toInt, parts(1).toInt)
    else
      Exact(s.toInt)

object CronSchedule:
  /**
   * Parse a cron expression string.
   *
   * Format: "minute hour day-of-month month day-of-week"
   * Example: "0 * * * *" (every hour)
   */
  def parse(expression: String): Either[String, KronSchedule] =
    val parts = expression.trim.split("\\s+")
    if parts.length != 5 then
      Left(s"Invalid cron expression: expected 5 fields, got ${parts.length}")
    else
      try
        val minute = KronField.parse(parts(0), 0, 59)
        val hour = KronField.parse(parts(1), 0, 23)
        val dayOfMonth = KronField.parse(parts(2), 1, 31)
        val month = KronField.parse(parts(3), 1, 12)
        val dayOfWeek = KronField.parse(parts(4), 0, 6)
        Right(KronSchedule(minute, hour, dayOfMonth, month, dayOfWeek))
      catch
        case e: Exception => Left(s"Failed to parse cron expression: ${e.getMessage}")
