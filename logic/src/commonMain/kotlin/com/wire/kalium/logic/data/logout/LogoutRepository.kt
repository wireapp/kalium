/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.logout

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.base.authenticated.logout.LogoutApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

interface LogoutRepository {

    /**
     * Listen to a logout event.
     * The event caries a [LogoutReason].
     */
    suspend fun observeLogout(): Flow<LogoutReason?>

    /**
     * Propagates the logout event and [reason],
     * listenable through [observeLogout]
     */
    suspend fun onLogout(reason: LogoutReason)

    /**
     * Informs the backend about the logout,
     * invalidating the current credentials.
     */
    suspend fun logout(): Either<CoreFailure, Unit>
}

internal class LogoutDataSource(
    private val logoutApi: LogoutApi,
) : LogoutRepository {

    private val logoutEventsChannel = Channel<LogoutReason?>(capacity = Channel.CONFLATED)

    override suspend fun observeLogout(): Flow<LogoutReason?> = logoutEventsChannel.receiveAsFlow()

    override suspend fun onLogout(reason: LogoutReason) {
        logoutEventsChannel.send(reason)
        // We need to clear channel state in case when user wants to login on the same session
        logoutEventsChannel.send(null)
    }

    override suspend fun logout(): Either<CoreFailure, Unit> =
        wrapApiRequest { logoutApi.logout() }

}
