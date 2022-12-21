package com.wire.kalium.logic.util

import com.wire.kalium.util.DateTimeUtil
import kotlinx.datetime.Instant
import org.junit.Test
import kotlin.test.assertEquals

class DateTimeUtilTest {

    private val isoDateTimeStringWith0Millis = "2022-12-20T17:30:00.000Z"
    private val isoDateTimeStringWith0MillisMinus1s = "2022-12-20T17:29:59.000Z"
    private val epochMillis = 1671557400000
    private val regex = Regex(DateTimeUtil.regex)

    @Test
    fun givenAValidIsoDateTimeString_whenMatchingRegex_thenShouldSucceed() {
        assert(regex.matches(isoDateTimeStringWith0Millis))
    }

    @Test
    fun givenAnInvalidIsoDateTimeString_whenMatchingRegex_thenShouldSucceed() {
        assert(!regex.matches("2022-12-20T17:30:00Z"))
        assert(!regex.matches("2022-12-20T17:30:00.AZ"))
        assert(!regex.matches("2022/12/20T17:30:00.000Z"))
        assert(!regex.matches("2022-12-2017:30:00.000Z"))
        assert(!regex.matches("2022-12-20T7:30:00Z"))
    }

    @Test
    fun givenAnInstantWithMillisEqualTo0_whenParsingToIsoString_thenMillisShouldNotBeIgnored() {
        val instant = Instant.fromEpochMilliseconds(epochMillis)
        val result = DateTimeUtil.fromInstantToIsoDateTimeString(instant)
        assert(regex.matches(result))
    }

    @Test
    fun givenAnIsoDateTimeStringMillisEqualTo0_whenParsingToInstantAndBackToIsoString_thenMillisShouldNotBeIgnored() {
        val instant = Instant.parse(isoDateTimeStringWith0Millis)
        val result = DateTimeUtil.fromInstantToIsoDateTimeString(instant)
        assert(regex.matches(result))
    }

    @Test
    fun givenAnIsoDateTimeStringMillisEqualTo0_whenParsingToEpochMillisAndBackToIsoString_thenMillisShouldNotBeIgnored() {
        val epochMillis = DateTimeUtil.fromIsoDateTimeStringToEpochMillis(isoDateTimeStringWith0Millis)
        val result = DateTimeUtil.fromEpochMillisToIsoDateTimeString(epochMillis)
        assert(regex.matches(result))
    }

    @Test
    fun givenAnIsoDateTimeStringMillisEqualTo0_whenSubtractingMillis_thenMillisShouldNotBeIgnored() {
        val result = DateTimeUtil.minusMilliseconds(isoDateTimeStringWith0Millis, 1000)
        assert(regex.matches(result))
    }

    @Test
    fun givenAnIsoDateTimeStringMillisEqualTo0_whenSubtractingMillis_thenValueShouldBeValid() {
        val expected = "2022-12-20T17:29:59.000Z"
        val result = DateTimeUtil.minusMilliseconds(isoDateTimeStringWith0Millis, 1000)
        assertEquals(expected, result)
    }

    @Test
    fun whenGettingCurrentIsoDateTimeString_thenMillisShouldNotBeIgnored() {
        val result = DateTimeUtil.currentIsoDateTimeString()
        assert(regex.matches(result))
    }

    @Test
    fun givenTwoValidIsoDateTimeStrings_whenCalculatingDifference_thenValueShouldBeValid() {
        val expected = isoDateTimeStringWith0MillisMinus1s
        val result = DateTimeUtil.minusMilliseconds(isoDateTimeStringWith0Millis, 1000)
        assertEquals(expected, result)
    }

    @Test
    fun givenTwoSameValidIsoDateTimeStrings_whenCalculatingDifference_thenValueShouldBe0() {
        val result = DateTimeUtil.calculateMillisDifference(isoDateTimeStringWith0Millis, isoDateTimeStringWith0MillisMinus1s)
        assertEquals(1000, result)
    }
}
