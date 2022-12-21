package com.wire.kalium.util

import kotlinx.datetime.Instant

actual open class PlatformDateTimeUtil actual constructor() {

    /**
     * Parse [kotlinx.datetime.Instant] into date-time string in ISO-8601 format.
     * Regular `.toString()` can return different results on different platforms, for instance jvm uses [java.time.Instant]
     * which can ignore milliseconds when equal to 0 (.000) and change the result from YYYY-MM-DDTHH:mm:ss.SSSZ format
     * to YYYY-MM-DDTHH:mm:ssZ.
     * @param instant date-time as [kotlinx.datetime.Instant]
     * @return date in ISO-8601 format (YYYY-MM-DDTHH:mm:ss.SSSZ)
     */
    actual fun fromInstantToIsoDateTimeString(instant: Instant): String =
        instant.toString() // TODO:"Implement own JS method"
}
