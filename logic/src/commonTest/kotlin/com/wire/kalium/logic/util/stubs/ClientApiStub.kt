/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.util.stubs

import com.wire.kalium.network.api.authenticated.client.ClientDTO
import com.wire.kalium.network.api.authenticated.client.RegisterClientRequest
import com.wire.kalium.network.api.authenticated.client.SimpleClientResponse
import com.wire.kalium.network.api.authenticated.client.UpdateClientCapabilitiesRequest
import com.wire.kalium.network.api.authenticated.client.UpdateClientMlsPublicKeysRequest
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.model.PushTokenBody
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.utils.NetworkResponse

open class ClientApiStub: ClientApi {
    override suspend fun registerClient(registerClientRequest: RegisterClientRequest): NetworkResponse<ClientDTO> {
        TODO("Test stub function was not yet implemented")
    }

    override suspend fun listClientsOfUsers(userIds: List<UserId>): NetworkResponse<Map<UserId, List<SimpleClientResponse>>> {
        TODO("Test stub function was not yet implemented")
    }

    override suspend fun fetchSelfUserClient(): NetworkResponse<List<ClientDTO>> {
        TODO("Test stub function was not yet implemented")
    }

    override suspend fun deleteClient(
        password: String?,
        clientID: String
    ): NetworkResponse<Unit> {
        TODO("Test stub function was not yet implemented")
    }

    override suspend fun fetchClientInfo(clientID: String): NetworkResponse<ClientDTO> {
        TODO("Test stub function was not yet implemented")
    }

    override suspend fun updateClientMlsPublicKeys(
        updateClientMlsPublicKeysRequest: UpdateClientMlsPublicKeysRequest,
        clientID: String
    ): NetworkResponse<Unit> {
        TODO("Test stub function was not yet implemented")
    }

    override suspend fun updateClientCapabilities(
        updateClientCapabilitiesRequest: UpdateClientCapabilitiesRequest,
        clientID: String
    ): NetworkResponse<Unit> {
        TODO("Test stub function was not yet implemented")
    }

    override suspend fun registerToken(body: PushTokenBody): NetworkResponse<Unit> {
        TODO("Test stub function was not yet implemented")
    }

    override suspend fun deregisterToken(pid: String): NetworkResponse<Unit> {
        TODO("Test stub function was not yet implemented")
    }
}
