package com.wire.kalium.network.api.prekey

import com.wire.kalium.network.api.KaliumHttpResult
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.message.QualifiedUserIdToClientMap
import com.wire.kalium.network.api.message.UserIdToClientMap

interface PreKeyApi {
    @Deprecated("use getUsersPreKey with QualifiedUserId")
    suspend fun getUsersPreKey(users: UserIdToClientMap): KaliumHttpResult<UserClientsToPreKeyMap>

    suspend fun getUsersPreKey(users: QualifiedUserIdToClientMap): KaliumHttpResult<QualifiedUserClientPrekeyMap>

}

typealias UserClientsToPreKeyMap = HashMap<String, HashMap<String, PreKey>>
typealias QualifiedUserClientPrekeyMap = HashMap<UserId, HashMap<String, PreKey>>
