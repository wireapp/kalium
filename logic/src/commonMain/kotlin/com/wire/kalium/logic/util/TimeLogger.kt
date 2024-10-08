/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.logic.util

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * The `TimeLogger` class is designed to measure the duration of a process for benchmarking purposes.
 * It takes a `processName` as a parameter to identify the process being timed.
 *
 * Usage:
 * - Call `start()` to begin tracking the time for a process.
 * - Call `finish()` to log the time taken since `start()` was invoked.
 *
 * @property processName The name of the process being measured.
 *
 * Methods:
 * - `start()`: Logs the process start and records the current time.
 * - `finish()`: Logs the process end, calculates the elapsed time, and prints the duration in milliseconds.
 *
 * This class is useful for performance analysis and optimization by providing a simple way to track
 * execution times for different parts of code.
 */
class TimeLogger(private val processName: String) {

    private lateinit var startTime: Instant
    fun start() {
        println("$processName starting")
        startTime = Clock.System.now()

    }

    fun finish() {
        val endTime = Clock.System.now()
        val duration = endTime - startTime
        println("$processName finished after: ${duration.inWholeMilliseconds} milliseconds")
    }

}
