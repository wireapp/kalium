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
package com.wire.kalium.logic.feature.search

import com.wire.kalium.logic.data.publicuser.model.UserSearchDetails
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId

data class SearchUserResult(
    val connected: List<UserSearchDetails>,
    val notConnected: List<UserSearchDetails>
) {
    internal companion object {
        inline fun resolveLocalAndRemoteResult(
            localResult: MutableMap<UserId, UserSearchDetails>,
            remoteSearch: MutableMap<UserId, UserSearchDetails>
        ): SearchUserResult {
            val updatedUser = mutableListOf<UserId>()
            remoteSearch.forEach { (userId, remoteUser) ->
                if (localResult.contains(userId) || (remoteUser.connectionStatus == ConnectionState.ACCEPTED)) {
                    localResult[userId] = remoteUser
                    updatedUser.add(userId)
                }
            }

            updatedUser.forEach { userId ->
                remoteSearch.remove(userId)
            }

            return SearchUserResult(
                connected = localResult.values.toList(),
                notConnected = remoteSearch.values.toList()
            )
        }
    }
}
