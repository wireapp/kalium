package com.wire.kalium.network.api.prekey

import com.wire.kalium.network.api.KaliumHttpResult
import com.wire.kalium.network.api.message.UserIdToClientMap

interface PreKeyApi {
    suspend fun getUsersPreKey(users: UserIdToClientMap): KaliumHttpResult<UserClientsToPreKeyMap>
}

typealias UserClientsToPreKeyMap = HashMap<String, HashMap<String, PreKey>>
