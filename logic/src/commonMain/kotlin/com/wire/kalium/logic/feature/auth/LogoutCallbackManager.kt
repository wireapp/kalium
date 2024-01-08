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
package com.wire.kalium.logic.feature.auth

import co.touchlab.stately.collections.ConcurrentMutableList
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.user.UserId

interface LogoutCallbackManager {
    fun register(callback: LogoutCallback)
    fun unregister(callback: LogoutCallback)
    suspend fun logout(userId: UserId, reason: LogoutReason)
}

class LogoutCallbackManagerImpl : LogoutCallbackManager {
    private val callbacks = ConcurrentMutableList<LogoutCallback>()
    override fun register(callback: LogoutCallback) { callbacks.add(callback) }
    override fun unregister(callback: LogoutCallback) { callbacks.remove(callback) }
    override suspend fun logout(userId: UserId, reason: LogoutReason) { callbacks.forEach { it(userId, reason) } }
}

typealias LogoutCallback = (userId: UserId, reason: LogoutReason) -> Unit
