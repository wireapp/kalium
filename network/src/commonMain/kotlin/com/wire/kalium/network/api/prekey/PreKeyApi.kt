package com.wire.kalium.network.api.prekey

import com.wire.kalium.network.api.KaliumHttpResult

import com.wire.kalium.network.api.message.UserIdToClientMap

interface PreKeyApi {
    @Deprecated("use getUsersPreKey with QualifiedUserId")
    suspend fun getUsersPreKey(users: UserIdToClientMap): KaliumHttpResult<UserClientsToPreKeyMap>

    /**
     * @param users a map of domain to (map of user IDs to client IDs)
     * @return a prekey for each one. You can't request information for more users than maximum conversation size.
     */
    suspend fun getUsersPreKey(users: DomainToUserIdToClientsMap): KaliumHttpResult<DomainToUserIdToClientsToPreykeyMap>

    suspend fun getClientAvailablePrekeys(clientId: String): KaliumHttpResult<List<Int>>

}

typealias UserClientsToPreKeyMap = Map<String, Map<String, PreKey>>
typealias DomainToUserIdToClientsToPreykeyMap = Map<String, Map<String, Map<String, PreKey>>>
typealias DomainToUserIdToClientsMap = Map<String, Map<String, List<String>>>

