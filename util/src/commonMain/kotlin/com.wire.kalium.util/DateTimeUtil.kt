package com.wire.kalium.util

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

// TODO(qol): we need to think if it should return an either or should we catch the exception,
// so far we assume that string date-times we use are always in valid ISO-8601 format so there shouldn't be any failed formatting
object DateTimeUtil : PlatformDateTimeUtil() {
    const val pattern: String = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
    const val regex = "^\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d\\d\\d(([+-]\\d\\d:\\d\\d)|Z)?\$"

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
        fromInstantToIsoDateTimeString(Instant.parse(isoDateTime).minus(millis, DateTimeUnit.MILLISECOND))

    /**
     * Return the current date-time as string
     * @return current date-time in ISO-8601 format (YYYY-MM-DDTHH:mm:ss.SSSZ)
     */
    fun currentIsoDateTimeString(): String =
        fromInstantToIsoDateTimeString(Clock.System.now())

    /**
     * Return the current date-time as [kotlinx.datetime.Instant].
     * It's parsed to string and back to ensure that it has the same proper accuracy (three decimal places - milliseconds)
     * @return current date-time as [kotlinx.datetime.Instant]
     */
    fun currentInstant(): Instant =
        Instant.parse(currentIsoDateTimeString())

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

    /**
     * Parse instant date-time into string date-time in ISO-8601 format
     * @receiver instant date-time as [kotlinx.datetime.Instant]
     * @return date in ISO-8601 format (YYYY-MM-DDTHH:mm:ss.SSSZ)
     */
    fun Instant.toIsoDateTimeString(): String = fromInstantToIsoDateTimeString(this)
}
