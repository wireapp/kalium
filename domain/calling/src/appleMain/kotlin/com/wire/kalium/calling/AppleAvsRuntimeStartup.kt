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

/**
 * Called under the bridge's startup lock. Native setup/run can leave partial global state on failure;
 * AVS has no supported retry/rollback contract, so both success and failure are cached for this process.
 */
internal class AppleAvsRuntimeStartup(
    private val setup: () -> Int,
    private val run: () -> Int,
    private val onStarted: () -> Unit,
) {
    private var outcome: Boolean? = null

    fun startIfAvailable(): Boolean {
        outcome?.let { return it }
        outcome = false
        val started = runCatching {
            if (setup() != 0 || run() != 0) {
                false
            } else {
                onStarted()
                true
            }
        }.getOrDefault(false)
        outcome = started
        return started
    }
}
