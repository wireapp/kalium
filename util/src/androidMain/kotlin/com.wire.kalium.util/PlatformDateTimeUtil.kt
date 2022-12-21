package com.wire.kalium.util

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone

actual open class PlatformDateTimeUtil actual constructor() {

    private val isoDateTimeFormat = SimpleDateFormat(DateTimeUtil.pattern, Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private val isoDateTimeFormatter = DateTimeFormatter.ofPattern(DateTimeUtil.pattern)

    /**
     * Parse [kotlinx.datetime.Instant] into date-time string in ISO-8601 format.
     * Regular `.toString()` can return different results on different platforms, for instance jvm uses [java.time.Instant]
     * which can ignore milliseconds when equal to 0 (.000) and change the result from YYYY-MM-DDTHH:mm:ss.SSSZ format
     * to YYYY-MM-DDTHH:mm:ssZ.
     * @param instant date-time as [kotlinx.datetime.Instant]
     * @return date in ISO-8601 format (YYYY-MM-DDTHH:mm:ss.SSSZ)
     */
    actual fun fromInstantToIsoDateTimeString(instant: Instant): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            isoDateTimeFormatter.format(instant.toJavaInstant())
        else
            isoDateTimeFormat.format(Date(instant.toEpochMilliseconds()))
}
