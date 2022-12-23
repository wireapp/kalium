package com.wire.kalium.logic.test_util

import com.wire.kalium.util.DateTimeUtil
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds

val Instant.wasInTheLastSecond: Boolean
    get() {
        val difference = DateTimeUtil.currentInstant() - this
        return difference < 1.seconds
    }
