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

package com.wire.kalium.network.api.base.authenticated.client

import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.api.base.model.PushTokenBody
import com.wire.kalium.network.utils.NetworkResponse

interface ClientApi {

    suspend fun registerClient(registerClientRequest: RegisterClientRequest): NetworkResponse<ClientResponse>

    suspend fun listClientsOfUsers(userIds: List<UserId>): NetworkResponse<Map<UserId, List<SimpleClientResponse>>>

    suspend fun fetchSelfUserClient(): NetworkResponse<List<ClientResponse>>

    suspend fun deleteClient(password: String?, clientID: String): NetworkResponse<Unit>

    suspend fun fetchClientInfo(clientID: String): NetworkResponse<ClientResponse>

    suspend fun updateClient(updateClientRequest: UpdateClientRequest, clientID: String): NetworkResponse<Unit>

    suspend fun registerToken(body: PushTokenBody): NetworkResponse<Unit>

    suspend fun deregisterToken(pid: String): NetworkResponse<Unit>
}
