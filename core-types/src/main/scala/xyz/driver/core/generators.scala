package xyz.driver.core

import enumeratum._
import java.math.MathContext
import java.time.{Instant, LocalDate, ZoneOffset}
import java.util.UUID

import xyz.driver.core.time.{Time, TimeOfDay, TimeRange}
import xyz.driver.core.date.{Date, DayOfWeek}

import scala.reflect.ClassTag
import scala.util.Random
import eu.timepit.refined.refineV
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection._

object generators {

  private val random = new Random
  import random._
  private val secureRandom = new java.security.SecureRandom()

  private val DefaultMaxLength       = 10
  private val StringLetters          = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ ".toSet
  private val NonAmbigiousCharacters = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
  private val Numbers                = "0123456789"

  private def nextTokenString(length: Int, chars: IndexedSeq[Char]): String = {
    val builder = new StringBuilder
    for (_ <- 0 until length) {
      builder += chars(secureRandom.nextInt(chars.length))
    }
    builder.result()
  }

  /** Creates a random invitation token.
    *
    * This token is meant fo human input and avoids using ambiguous characters such as 'O' and '0'. It
    * therefore contains less entropy and is not meant to be used as a cryptographic secret. */
  @deprecated(
    "The term 'token' is too generic and security and readability conventions are not well defined. " +
      "Services should implement their own version that suits their security requirements.",
    "1.11.0"
  )
  def nextToken(length: Int): String = nextTokenString(length, NonAmbigiousCharacters)

  @deprecated(
    "The term 'token' is too generic and security and readability conventions are not well defined. " +
      "Services should implement their own version that suits their security requirements.",
    "1.11.0"
  )
  def nextNumericToken(length: Int): String = nextTokenString(length, Numbers)

  def nextInt(maxValue: Int, minValue: Int = 0): Int = random.nextInt(maxValue - minValue) + minValue

  def nextBoolean(): Boolean = random.nextBoolean()

  def nextDouble(): Double = random.nextDouble()

  def nextId[T](): Id[T] = Id[T](nextUuid().toString)

  def nextId[T](maxLength: Int): Id[T] = Id[T](nextString(maxLength))

  def nextNumericId[T](): Id[T] = Id[T](nextLong.abs.toString)

  def nextNumericId[T](maxValue: Int): Id[T] = Id[T](nextInt(maxValue).toString)

  def nextName[T](maxLength: Int = DefaultMaxLength): Name[T] = Name[T](nextString(maxLength))

  def nextNonEmptyName[T](maxLength: Int = DefaultMaxLength): NonEmptyName[T] =
    NonEmptyName[T](nextNonEmptyString(maxLength))

  def nextUuid(): UUID = java.util.UUID.randomUUID

  def nextRevision[T](): Revision[T] = Revision[T](nextUuid().toString)

  def nextString(maxLength: Int = DefaultMaxLength): String =
    (oneOf[Char](StringLetters) +: arrayOf(oneOf[Char](StringLetters), maxLength - 1)).mkString

  def nextNonEmptyString(maxLength: Int = DefaultMaxLength): String Refined NonEmpty = {
    refineV[NonEmpty](
      (oneOf[Char](StringLetters) +: arrayOf(oneOf[Char](StringLetters), maxLength - 1)).mkString
    ).right.get
  }

  def nextOption[T](value: => T): Option[T] = if (nextBoolean()) Option(value) else None

  def nextPair[L, R](left: => L, right: => R): (L, R) = (left, right)

  def nextTriad[F, S, T](first: => F, second: => S, third: => T): (F, S, T) = (first, second, third)

  def nextInstant(): Instant = Instant.ofEpochMilli(math.abs(nextLong() % System.currentTimeMillis))

  def nextTime(): Time = nextInstant()

  def nextTimeOfDay: TimeOfDay = TimeOfDay(java.time.LocalTime.MIN.plusSeconds(nextLong), java.util.TimeZone.getDefault)

  def nextTimeRange(): TimeRange = {
    val oneTime     = nextTime()
    val anotherTime = nextTime()

    TimeRange(
      Time(scala.math.min(oneTime.millis, anotherTime.millis)),
      Time(scala.math.max(oneTime.millis, anotherTime.millis)))
  }

  def nextDate(): Date = nextTime().toDate(java.util.TimeZone.getTimeZone("UTC"))

  def nextLocalDate(): LocalDate = nextInstant().atZone(ZoneOffset.UTC).toLocalDate

  def nextDayOfWeek(): DayOfWeek = oneOf(DayOfWeek.All)

  def nextBigDecimal(multiplier: Double = 1000000.00, precision: Int = 2): BigDecimal =
    BigDecimal(multiplier * nextDouble, new MathContext(precision))

  def oneOf[T](items: T*): T = oneOf(items.toSet)

  def oneOf[T](items: Set[T]): T = items.toSeq(nextInt(items.size))

  def oneOf[T <: EnumEntry](enum: Enum[T]): T = oneOf(enum.values: _*)

  def arrayOf[T: ClassTag](generator: => T, maxLength: Int = DefaultMaxLength, minLength: Int = 0): Array[T] =
    Array.fill(nextInt(maxLength, minLength))(generator)

  def seqOf[T](generator: => T, maxLength: Int = DefaultMaxLength, minLength: Int = 0): Seq[T] =
    Seq.fill(nextInt(maxLength, minLength))(generator)

  def vectorOf[T](generator: => T, maxLength: Int = DefaultMaxLength, minLength: Int = 0): Vector[T] =
    Vector.fill(nextInt(maxLength, minLength))(generator)

  def listOf[T](generator: => T, maxLength: Int = DefaultMaxLength, minLength: Int = 0): List[T] =
    List.fill(nextInt(maxLength, minLength))(generator)

  def setOf[T](generator: => T, maxLength: Int = DefaultMaxLength, minLength: Int = 0): Set[T] =
    seqOf(generator, maxLength, minLength).toSet

  def mapOf[K, V](
      keyGenerator: => K,
      valueGenerator: => V,
      maxLength: Int = DefaultMaxLength,
      minLength: Int = 0): Map[K, V] =
    seqOf(nextPair(keyGenerator, valueGenerator), maxLength, minLength).toMap
}
