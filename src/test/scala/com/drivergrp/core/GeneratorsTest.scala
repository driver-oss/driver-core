package com.drivergrp.core

import org.scalatest.{Assertions, FlatSpec, Matchers}

class GeneratorsTest extends FlatSpec with Matchers with Assertions {
  import generators._

  "Generators" should "be able to generate com.drivergrp.core.Id identifiers" in {

    val generatedId1 = nextId[String]()
    val generatedId2 = nextId[String]()
    val generatedId3 = nextId[Long]()

    generatedId1 should be >= 0L
    generatedId2 should be >= 0L
    generatedId3 should be >= 0L
    generatedId1 should not be generatedId2
    generatedId2 should !==(generatedId3)
  }

  it should "be able to generate com.drivergrp.core.Id identifiers with max value" in {

    val generatedLimitedId1 = nextId[String](10000)
    val generatedLimitedId2 = nextId[String](1000)
    val generatedLimitedId3 = nextId[Long](2000)

    generatedLimitedId1 should be >= 0L
    generatedLimitedId1 should be < 10000L
    generatedLimitedId2 should be >= 0L
    generatedLimitedId2 should be < 1000L
    generatedLimitedId3 should be >= 0L
    generatedLimitedId3 should be < 2000L
    generatedLimitedId1 should not be generatedLimitedId2
    generatedLimitedId2 should !==(generatedLimitedId3)
  }

  it should "be able to generate com.drivergrp.core.Name names" in {

    nextName[String]() should not be nextName[String]()
    nextName[String]().length should be >= 0

    val fixedLengthName = nextName[String](10)
    fixedLengthName.length should be <= 10
    assert(!fixedLengthName.exists(_.isControl))
  }

  it should "be able to generate proper UUIDs" in {

    nextUuid() should not be nextUuid()
    nextUuid().toString.length should be (36)
  }

  it should "be able to generate new Revisions" in {

    nextRevision[String]() should not be nextRevision[String]()
    nextRevision[String]().id.length should be > 0
  }

  it should "be able to generate strings" in {

    nextString() should not be nextString()
    nextString().length should be >= 0

    val fixedLengthString = nextString(20)
    fixedLengthString.length should be <= 20
    assert(!fixedLengthString.exists(_.isControl))
  }

  it should "be able to generate options which are sometimes have values and sometimes not" in {

    val generatedOption = nextOption("2")

    generatedOption should not contain "1"
    assert(generatedOption === Some("2") || generatedOption === None)
  }

  it should "be able to generate a pair of two generated values" in {

    val constantPair = nextPair("foo", 1L)
    constantPair._1 should be("foo")
    constantPair._2 should be(1L)

    val generatedPair = nextPair(nextId[Int](), nextName[Int]())

    generatedPair._1 should be > 0L
    generatedPair._2.length should be > 0

    nextPair(nextId[Int](), nextName[Int]()) should not be
    nextPair(nextId[Int](), nextName[Int]())
  }

  it should "be able to generate a triad of two generated values" in {

    val constantTriad = nextTriad("foo", "bar", 1L)
    constantTriad._1 should be("foo")
    constantTriad._2 should be("bar")
    constantTriad._3 should be(1L)

    val generatedTriad = nextTriad(nextId[Int](), nextName[Int](), nextBigDecimal())

    generatedTriad._1 should be > 0L
    generatedTriad._2.length should be > 0
    generatedTriad._3 should be >= BigDecimal(0.00)

    nextTriad(nextId[Int](), nextName[Int](), nextBigDecimal()) should not be
    nextTriad(nextId[Int](), nextName[Int](), nextBigDecimal())
  }

  it should "be able to generate a time value" in {

    val generatedTime = nextTime()
    val currentTime   = System.currentTimeMillis()

    generatedTime.millis should be >= 0L
    generatedTime.millis should be <= currentTime
  }

  it should "be able to generate a time range value" in {

    val generatedTimeRange = nextTimeRange()
    val currentTime        = System.currentTimeMillis()

    generatedTimeRange.start.millis should be >= 0L
    generatedTimeRange.start.millis should be <= currentTime
    generatedTimeRange.end.millis should be >= 0L
    generatedTimeRange.end.millis should be <= currentTime
    generatedTimeRange.start.millis should be <= generatedTimeRange.end.millis
  }

  it should "be able to generate a BigDecimal value" in {

    val defaultGeneratedBigDecimal = nextBigDecimal()

    defaultGeneratedBigDecimal should be >= BigDecimal(0.00)
    defaultGeneratedBigDecimal should be <= BigDecimal(1000000.00)
    defaultGeneratedBigDecimal.precision should be(2)

    val unitIntervalBigDecimal = nextBigDecimal(1.00, 8)

    unitIntervalBigDecimal should be >= BigDecimal(0.00)
    unitIntervalBigDecimal should be <= BigDecimal(1.00)
    unitIntervalBigDecimal.precision should be(8)
  }

  it should "be able to generate a specific value from a set of values" in {

    val possibleOptions = Set(1, 3, 5, 123, 0, 9)

    val pick1 = generators.oneOf(possibleOptions)
    val pick2 = generators.oneOf(possibleOptions)
    val pick3 = generators.oneOf(possibleOptions)

    possibleOptions should contain(pick1)
    possibleOptions should contain(pick2)
    possibleOptions should contain(pick3)

    val pick4 = generators.oneOf(1, 3, 5, 123, 0, 9)
    val pick5 = generators.oneOf(1, 3, 5, 123, 0, 9)
    val pick6 = generators.oneOf(1, 3, 5, 123, 0, 9)

    possibleOptions should contain(pick4)
    possibleOptions should contain(pick5)
    possibleOptions should contain(pick6)

    Set(pick1, pick2, pick3, pick4, pick5, pick6).size should be >= 1
  }

  it should "be able to generate array with values generated by generators" in {

    val arrayOfTimes = arrayOf(nextTime(), 16)
    arrayOfTimes.length should be <= 16

    val arrayOfBigDecimals = arrayOf(nextBigDecimal(), 8)
    arrayOfBigDecimals.length should be <= 8
  }

  it should "be able to generate seq with values generated by generators" in {

    val seqOfTimes = seqOf(nextTime(), 16)
    seqOfTimes.size should be <= 16

    val seqOfBigDecimals = seqOf(nextBigDecimal(), 8)
    seqOfBigDecimals.size should be <= 8
  }

  it should "be able to generate vector with values generated by generators" in {

    val vectorOfTimes = vectorOf(nextTime(), 16)
    vectorOfTimes.size should be <= 16

    val vectorOfStrings = seqOf(nextString(), 8)
    vectorOfStrings.size should be <= 8
  }

  it should "be able to generate list with values generated by generators" in {

    val listOfTimes = listOf(nextTime(), 16)
    listOfTimes.size should be <= 16

    val listOfBigDecimals = seqOf(nextBigDecimal(), 8)
    listOfBigDecimals.size should be <= 8
  }

  it should "be able to generate set with values generated by generators" in {

    val setOfTimes = vectorOf(nextTime(), 16)
    setOfTimes.size should be <= 16

    val setOfBigDecimals = seqOf(nextBigDecimal(), 8)
    setOfBigDecimals.size should be <= 8
  }

  it should "be able to generate maps with keys and values generated by generators" in {

    val generatedConstantMap = mapOf(10, "key", 123)
    generatedConstantMap.size should be <= 1
    assert(generatedConstantMap.keys.forall(_ == "key"))
    assert(generatedConstantMap.values.forall(_ == 123))

    val generatedMap = mapOf(10, nextString(10), nextBigDecimal())
    assert(generatedMap.keys.forall(_.length <= 10))
    assert(generatedMap.values.forall(_ >= BigDecimal(0.00)))
  }

  it should "compose deeply" in {

    val generatedNestedMap = mapOf(10, nextString(10), nextPair(nextBigDecimal(), nextOption(123)))

    generatedNestedMap.size should be <= 10
    generatedNestedMap.keySet.size should be <= 10
    generatedNestedMap.values.size should be <= 10
    assert(generatedNestedMap.values.forall(value => !value._2.exists(_ != 123)))
  }
}
