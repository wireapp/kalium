package com.wire.kalium.api.prekey

import com.wire.kalium.api.KaliumHttpResult
import com.wire.kalium.api.message.UserIdToClientMap
import com.wire.kalium.models.outbound.otr.PreKey

interface PreKeyApi {
    suspend fun getUsersPreKey(users: UserIdToClientMap): KaliumHttpResult<MapUserClientsToPreKey>
}


typealias MapUserClientsToPreKey = HashMap<String, HashMap<String, PreKey>>
