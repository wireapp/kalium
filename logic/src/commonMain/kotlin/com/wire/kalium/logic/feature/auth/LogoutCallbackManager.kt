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
import io.mockative.Mockable

/**
 * This manager is used to register callbacks that will be called when a user session is being logged out.
 * The app may have some actions to perform outside the kalium as well when a user is logged out, like clearing the data store, etc.
 * When logout action is triggered by the user, then the app can execute these actions right after the [LogoutUseCase] result,
 * but when the logout is triggered automatically by some event (i.e. session expired, device removed, account removed),
 * then the app will not be able to execute these actions without registering to this manager.
 */
interface LogoutCallbackManager {
    fun register(callback: LogoutCallback)
    fun unregister(callback: LogoutCallback)
}

internal class LogoutCallbackManagerImpl : LogoutCallbackManager, LogoutCallback {
    private val callbacks = ConcurrentMutableList<LogoutCallback>()
    override fun register(callback: LogoutCallback) { callbacks.add(callback) }
    override fun unregister(callback: LogoutCallback) { callbacks.remove(callback) }
    override suspend fun invoke(userId: UserId, reason: LogoutReason) { callbacks.forEach { it(userId, reason) } }
}

@Mockable
interface LogoutCallback {
    suspend operator fun invoke(userId: UserId, reason: LogoutReason)
}
