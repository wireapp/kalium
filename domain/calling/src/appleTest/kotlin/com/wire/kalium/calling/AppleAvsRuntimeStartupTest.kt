/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.calling

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppleAvsRuntimeStartupTest {
    @Test
    fun successfulStartupRunsOnceInOrder() {
        val events = mutableListOf<String>()
        val startup = AppleAvsRuntimeStartup(
            setup = {
                events += "setup"
                0
            },
            run = {
                events += "run"
                0
            },
            onStarted = { events += "started" },
        )
        assertTrue(startup.startIfAvailable())
        assertTrue(startup.startIfAvailable())
        assertEquals(listOf("setup", "run", "started"), events)
    }

    @Test
    fun setupFailureSkipsRunAndCannotRetry() {
        val events = mutableListOf<String>()
        val startup = AppleAvsRuntimeStartup(
            setup = {
                events += "setup"
                5
            },
            run = {
                events += "run"
                0
            },
            onStarted = { events += "started" },
        )
        assertFalse(startup.startIfAvailable())
        assertFalse(startup.startIfAvailable())
        assertEquals(listOf("setup"), events)
    }

    @Test
    fun runFailureNeverReportsSuccessOrRetriesSetup() {
        val events = mutableListOf<String>()
        val startup = AppleAvsRuntimeStartup(
            setup = {
                events += "setup"
                0
            },
            run = {
                events += "run"
                5
            },
            onStarted = { events += "started" },
        )
        assertFalse(startup.startIfAvailable())
        assertFalse(startup.startIfAvailable())
        assertEquals(listOf("setup", "run"), events)
    }

    @Test
    fun thrownFailureCannotRetryNativeStartup() {
        var attempts = 0
        val startup = AppleAvsRuntimeStartup(
            setup = { 0 },
            run = {
                attempts++
                error("native startup failed")
            },
            onStarted = { error("must not report started") },
        )
        assertFalse(startup.startIfAvailable())
        assertFalse(startup.startIfAvailable())
        assertEquals(1, attempts)
    }
}
