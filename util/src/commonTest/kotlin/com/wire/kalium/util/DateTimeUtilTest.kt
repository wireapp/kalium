package com.wire.kalium.util

import com.wire.kalium.util.string.IgnoreIOS
import com.wire.kalium.util.string.IgnoreJS
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@IgnoreJS
@IgnoreIOS // TODO investigate why tests are failing, timestamp precision?
class DateTimeUtilTest {

    private val isoDateTimeStringWith0Millis = "2022-12-20T17:30:00.000Z"
    private val isoDateTimeStringWith0MillisMinus1s = "2022-12-20T17:29:59.000Z"
    private val epochMillis = 1671557400000
    private val regex = Regex(DateTimeUtil.regex)

    @Test
    fun givenAValidIsoDateTimeString_whenMatchingRegex_thenShouldSucceed() {
        assertTrue(regex.matches(isoDateTimeStringWith0Millis))
    }

    @Test
    fun givenAnInvalidIsoDateTimeString_whenMatchingRegex_thenShouldSucceed() {
        assertTrue(!regex.matches("2022-12-20T17:30:00Z"))
        assertTrue(!regex.matches("2022-12-20T17:30:00.AZ"))
        assertTrue(!regex.matches("2022/12/20T17:30:00.000Z"))
        assertTrue(!regex.matches("2022-12-2017:30:00.000Z"))
        assertTrue(!regex.matches("2022-12-20T7:30:00Z"))
    }

    @Test
    fun givenAnInstantWithMillisEqualTo0_whenParsingToIsoString_thenMillisShouldNotBeIgnored() {
        val instant = Instant.fromEpochMilliseconds(epochMillis)
        val result = DateTimeUtil.fromInstantToIsoDateTimeString(instant)
        assertTrue(regex.matches(result))
    }

    @Test
    fun givenAnIsoDateTimeStringMillisEqualTo0_whenParsingToInstantAndBackToIsoString_thenMillisShouldNotBeIgnored() {
        val instant = Instant.parse(isoDateTimeStringWith0Millis)
        val result = DateTimeUtil.fromInstantToIsoDateTimeString(instant)
        assertTrue(regex.matches(result))
    }

    @Test
    fun givenAnIsoDateTimeStringMillisEqualTo0_whenParsingToEpochMillisAndBackToIsoString_thenMillisShouldNotBeIgnored() {
        val epochMillis = DateTimeUtil.fromIsoDateTimeStringToEpochMillis(isoDateTimeStringWith0Millis)
        val result = DateTimeUtil.fromEpochMillisToIsoDateTimeString(epochMillis)
        assertTrue(regex.matches(result))
    }

    @Test
    fun givenAnIsoDateTimeStringMillisEqualTo0_whenSubtractingMillis_thenMillisShouldNotBeIgnored() {
        val result = DateTimeUtil.minusMilliseconds(isoDateTimeStringWith0Millis, 1000)
        assertTrue(regex.matches(result))
    }

    @Test
    fun givenAnIsoDateTimeStringMillisEqualTo0_whenSubtractingMillis_thenValueShouldBeValid() {
        val expected = isoDateTimeStringWith0MillisMinus1s
        val result = DateTimeUtil.minusMilliseconds(isoDateTimeStringWith0Millis, 1000)
        assertEquals(expected, result)
    }

    @Test
    fun whenGettingCurrentIsoDateTimeString_thenMillisShouldNotBeIgnored() {
        val result = DateTimeUtil.currentIsoDateTimeString()
        assertTrue(regex.matches(result))
    }

    @Test
    fun givenTwoValidIsoDateTimeStrings_whenCalculatingDifference_thenValueShouldBeValid() {
        val expected = isoDateTimeStringWith0MillisMinus1s
        val result = DateTimeUtil.minusMilliseconds(isoDateTimeStringWith0Millis, 1000)
        assertEquals(expected, result)
    }

    @Test
    fun givenTwoSameValidIsoDateTimeStrings_whenCalculatingDifference_thenValueShouldBeValid() {
        val result = DateTimeUtil.calculateMillisDifference(isoDateTimeStringWith0MillisMinus1s, isoDateTimeStringWith0Millis)
        assertEquals(1000, result)
    }

    @Test
    fun givenTwoSameValidIsoDateTimeStrings_whenCalculatingDifference_thenValueShouldBe0() {
        val result = DateTimeUtil.calculateMillisDifference(isoDateTimeStringWith0Millis, isoDateTimeStringWith0Millis)
        assertEquals(0, result)
    }
}
