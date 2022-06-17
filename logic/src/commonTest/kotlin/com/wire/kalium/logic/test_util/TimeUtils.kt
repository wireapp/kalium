package com.wire.kalium.logic.test_util

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds

val Instant.wasInTheLastSecond: Boolean
    get() {
        val difference = Clock.System.now() - this
        return difference < 1.seconds
    }
