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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.wire.kalium.calling

import avs.AVSMediaManager
import avs.wcall_run
import avs.wcall_setup

/** On macOS, wcall owns AVS's event loop. The bridge calls [setup] then [run] once, under its start lock. */
internal object AppleAvsPlatformRuntime {
    fun setup(): Int = wcall_setup()

    fun run(): Int = wcall_run()

    fun mediaManager(): AVSMediaManager? = if (AppleAvs.bridge.startIfAvailable()) AVSMediaManager.defaultMediaManager() else null
}
