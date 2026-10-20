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

package com.wire.kalium.calling

/** Registration is process scoped: replacing a runtime could discard callbacks still owned by native code. */
object JvmCallingProvider {
    @Volatile
    private var registered: LifecycleCalling? = null

    @Synchronized
    fun register(calling: LifecycleCalling) {
        check(registered == null || registered === calling) { "A JVM calling runtime is already registered" }
        registered = calling
    }

    internal fun get(): LifecycleCalling = checkNotNull(registered) {
        "Register a LifecycleCalling implementation with JvmCallingProvider before enabling JVM calling"
    }
}

fun callingForCurrentPlatform(): Calling = JvmCallingProvider.get()
