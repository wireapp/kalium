package com.wire.kalium.util.time

import kotlinx.datetime.Instant

const val UNIX_FIRST_DATE: String = "1970-01-01T00:00:00.000Z"

val Instant.Companion.UNIX_FIRST_DATE get() = fromEpochMilliseconds(0L)
