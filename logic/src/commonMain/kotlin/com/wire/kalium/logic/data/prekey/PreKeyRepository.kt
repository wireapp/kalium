package com.wire.kalium.logic.data.prekey

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.prekey.remote.PreKeyRemoteDataSource
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either

interface PreKeyRepository {
    suspend fun preKeysOfClientsByQualifiedUsers(
        qualifiedIdsMap: Map<UserId, List<ClientId>>
    ): Either<CoreFailure, List<QualifiedUserPreKeyInfo>>
}

class PreKeyDataSource(private val preKeyRemoteDataSource: PreKeyRemoteDataSource) : PreKeyRepository {
    override suspend fun preKeysOfClientsByQualifiedUsers(
        qualifiedIdsMap: Map<UserId, List<ClientId>>
    ): Either<CoreFailure, List<QualifiedUserPreKeyInfo>> = preKeyRemoteDataSource.preKeysForMultipleQualifiedUsers(qualifiedIdsMap)
}
