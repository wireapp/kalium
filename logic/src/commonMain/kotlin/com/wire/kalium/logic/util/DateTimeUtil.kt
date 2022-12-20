package com.wire.kalium.logic.util

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.until

expect open class PlatformDateTimeUtil() {

    /**
     * Parse [kotlinx.datetime.Instant] into date-time string in ISO-8601 format.
     * Regular `.toString()` can return different results on different platforms, for instance jvm uses [java.time.Instant]
     * which can ignore milliseconds when equal to 0 (.000) and change the result from YYYY-MM-DDTHH:mm:ss.SSSZ format
     * to YYYY-MM-DDTHH:mm:ssZ.
     * @param instant date-time as [kotlinx.datetime.Instant]
     * @return date in ISO-8601 format (YYYY-MM-DDTHH:mm:ss.SSSZ)
     */
    fun fromInstantToIsoDateTimeString(instant: Instant): String
}

//TODO(qol): we need to think if it should return an either or should we catch the exception,
// so far we assume that string date-times we use are always in valid ISO-8601 format so there shouldn't be any failed formatting
object DateTimeUtil : PlatformDateTimeUtil() {
    const val pattern: String = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"

    /**
     * Calculate the difference between two date-times provided to it
     * @param isoDateTime1 date-time in ISO-8601 format (YYYY-MM-DDTHH:mm:ss.SSSZ)
     * @param isoDateTime2 date-time in ISO-8601 format (YYYY-MM-DDTHH:mm:ss.SSSZ)
     * @return difference between two provided date-times in milliseconds
     */
    fun calculateMillisDifference(isoDateTime1: String, isoDateTime2: String): Long =
        isoDateTime1.toInstant().until(isoDateTime2.toInstant(), DateTimeUnit.MILLISECOND)

    /**
     * Subtract milliseconds from the given date-time
     * @param isoDateTime date-time in ISO-8601 format (YYYY-MM-DDTHH:mm:ss.SSSZ)
     * @param millis
     * @return date-time in ISO-8601 format (YYYY-MM-DDTHH:mm:ss.SSSZ) decreased by the given milliseconds
     */
    fun minusMilliseconds(isoDateTime: String, millis: Long): String =
        Instant.parse(isoDateTime).minus(millis, DateTimeUnit.MILLISECOND).toString()

    /**
     * Return the current date-time
     * @return current date-time in ISO-8601 format (YYYY-MM-DDTHH:mm:ss.SSSZ)
     */
    fun currentIsoDateTimeString(): String =
        fromInstantToIsoDateTimeString(Clock.System.now())

    /**
     * Parse epoch timestamp in milliseconds into date-time in ISO-8601 format
     * @param timestamp epoch timestamp in milliseconds
     * @return date in ISO-8601 format (YYYY-MM-DDTHH:mm:ss.SSSZ)
     */
    fun fromEpochMillisToIsoDateTimeString(timestamp: Long): String =
        fromInstantToIsoDateTimeString(Instant.fromEpochMilliseconds(timestamp))

    /**
     * Parse date-time in ISO-8601 format into epoch timestamp in milliseconds
     * @param isoDateTime date in ISO-8601 format (YYYY-MM-DDTHH:mm:ss.SSSZ)
     * @return epoch timestamp in milliseconds
     */
    fun fromIsoDateTimeStringToEpochMillis(isoDateTime: String): Long =
        Instant.parse(isoDateTime).toEpochMilliseconds()

    /**
     * Parse date-time in ISO-8601 format into epoch timestamp in milliseconds
     * @receiver date in ISO-8601 format (YYYY-MM-DDTHH:mm:ss.SSSZ)
     * @return epoch timestamp in milliseconds
     */
    fun String.toEpochMillis(): Long = fromIsoDateTimeStringToEpochMillis(this)

    /**
     * Parse epoch timestamp in milliseconds into date-time in ISO-8601 format
     * @receiver epoch timestamp in milliseconds
     * @return date in ISO-8601 format (YYYY-MM-DDTHH:mm:ss.SSSZ)
     */
    fun Long.toIsoDateTimeString(): String = fromEpochMillisToIsoDateTimeString(this)
}

