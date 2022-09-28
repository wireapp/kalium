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
