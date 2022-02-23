package com.wire.kalium.logic.data.prekey.remote

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.prekey.QualifiedUserPreKeyInfo
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.prekey.PreKeyApi

interface PreKeyRemoteRepository {
    suspend fun preKeysForMultipleQualifiedUsers(
        qualifiedIdMap: Map<UserId, List<ClientId>>
    ): Either<CoreFailure, List<QualifiedUserPreKeyInfo>>
}

class PreKeyRemoteDataSource(
    private val preKeyApi: PreKeyApi,
    private val preKeyListMapper: PreKeyListMapper
) : PreKeyRemoteRepository {

    //TODO unit test to be created later
    override suspend fun preKeysForMultipleQualifiedUsers(
        qualifiedIdMap: Map<UserId, List<ClientId>>
    ): Either<CoreFailure, List<QualifiedUserPreKeyInfo>> =
        wrapApiRequest {
            preKeyApi.getUsersPreKey(
                preKeyListMapper.toRemoteClientPreKeyInfoTo(qualifiedIdMap)
            )
        }.map { preKeyListMapper.fromRemoteQualifiedPreKeyInfoMap(it) }
}
