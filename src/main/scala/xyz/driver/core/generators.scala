package xyz.driver.core

import java.math.MathContext
import java.util.UUID

import xyz.driver.core.time.{Time, TimeRange}
import xyz.driver.core.date.Date

import scala.reflect.ClassTag
import scala.util.Random
import eu.timepit.refined.refineV
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection._

object generators {

  private val random = new Random
  import random._

  private val DefaultMaxLength       = 10
  private val StringLetters          = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ ".toSet
  private val NonAmbigiousCharacters = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789".toSet

  def nextToken(length: Int): String = List.fill(length)(oneOf(NonAmbigiousCharacters)).mkString

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

  def nextTime(): Time = Time(math.abs(nextLong() % System.currentTimeMillis))

  def nextTimeRange(): TimeRange = {
    val oneTime     = nextTime()
    val anotherTime = nextTime()

    TimeRange(
      Time(scala.math.min(oneTime.millis, anotherTime.millis)),
      Time(scala.math.max(oneTime.millis, anotherTime.millis)))
  }

  def nextDate(): Date = nextTime().toDate(java.util.TimeZone.getTimeZone("UTC"))

  def nextBigDecimal(multiplier: Double = 1000000.00, precision: Int = 2): BigDecimal =
    BigDecimal(multiplier * nextDouble, new MathContext(precision))

  def oneOf[T](items: T*): T = oneOf(items.toSet)

  def oneOf[T](items: Set[T]): T = items.toSeq(nextInt(items.size))

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
