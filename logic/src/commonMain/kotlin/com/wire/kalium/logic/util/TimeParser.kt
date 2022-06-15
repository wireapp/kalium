package com.wire.kalium.logic.util

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.until

//TODO(qol): we need to think if this TimeParser should return an either, should we catch the exception ?
interface TimeParser {
    /**
     * Calculate the difference between two time stamps provided to it
     *  The date time passed to @param[time1] and @param[time2] needs to have I.SO-8601 format
     *  eq.YYYY-MM-DDTHH:mm:ss.SSSZ
     *
     */
    fun calculateMillisDifference(time1: String, time2: String): Long

    /**
     * @return time stamp that is smaller than given one for @param[millis] milliseconds
     */
    fun dateMinusMilliseconds(date: String, millis: Long): String

    /**
     * @return current time stamp
     */
    fun currentTimeStamp(): String
}

class TimeParserImpl : TimeParser {

    override fun calculateMillisDifference(time1: String, time2: String): Long {
        return time1.toInstant().until(time2.toInstant(), DateTimeUnit.MILLISECOND)
    }

    override fun dateMinusMilliseconds(date: String, millis: Long): String {
        return Instant.parse(date).minus(millis, DateTimeUnit.MILLISECOND).toString()
    }

    override fun currentTimeStamp(): String {
        return Clock.System.now().toString()
    }

}
