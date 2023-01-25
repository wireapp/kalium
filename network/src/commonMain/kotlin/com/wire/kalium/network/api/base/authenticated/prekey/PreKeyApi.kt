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

package com.wire.kalium.network.api.base.authenticated.prekey

import com.wire.kalium.network.utils.NetworkResponse

interface PreKeyApi {
    /**
     * @param users a map of domain to (map of user IDs to client IDs)
     * @return a prekey for each one. You can't request information for more users than maximum conversation size.
     */
    suspend fun getUsersPreKey(
        users: Map<String, Map<String, List<String>>>
    ): NetworkResponse<Map<String, Map<String, Map<String, PreKeyDTO?>>>>

    suspend fun getClientAvailablePrekeys(clientId: String): NetworkResponse<List<Int>>

}

typealias DomainToUserIdToClientsToPreKeyMap = Map<String, Map<String, Map<String, PreKeyDTO?>>>
typealias DomainToUserIdToClientsMap = Map<String, Map<String, List<String>>>
