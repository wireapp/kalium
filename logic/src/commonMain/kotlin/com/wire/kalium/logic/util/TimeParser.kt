package com.wire.kalium.logic.util

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.toInstant
import kotlinx.datetime.until

//TODO: we need to think if this TimeParser should return an either, should we catch the exception ?
interface TimeParser {
    fun calculateMillisDifference(time1: String, time2: String): Long
}

class TimeParserImpl : TimeParser {

    override fun calculateMillisDifference(time1: String, time2: String): Long {
        return time1.toInstant().until(time2.toInstant(), DateTimeUnit.MILLISECOND)
    }

}
