package com.wire.kalium.logic.data.prekey.remote

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.prekey.PreKeyMapper
import com.wire.kalium.logic.data.prekey.QualifiedUserPreKeyInfo
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.prekey.PreKeyApi
import com.wire.kalium.network.utils.isSuccessful

interface PreKeyRemoteRepository {
    suspend fun preKeysForMultipleQualifiedUsers(
        qualifiedIdMap: Map<QualifiedID, List<String>>
    ): Either<CoreFailure, List<QualifiedUserPreKeyInfo>>
}

class PreKeyRemoteDataSource(
    private val preKeyApi: PreKeyApi,
    private val preKeyMapper: PreKeyMapper
) : PreKeyRemoteRepository {

    override suspend fun preKeysForMultipleQualifiedUsers(
        qualifiedIdMap: Map<QualifiedID, List<String>>
    ): Either<CoreFailure, List<QualifiedUserPreKeyInfo>> {

        val mappedUsers = preKeyMapper.toRemoteClientPreKeyInfoTo(qualifiedIdMap)
        val response = preKeyApi.getUsersPreKey(mappedUsers)
        return if (response.isSuccessful()) {
            val mappedResponse = preKeyMapper.fromRemoteQualifiedPreKeyInfoMap(response.value)
            Either.Right(mappedResponse)
        } else {
            //TODO handle error
            Either.Left(CoreFailure.ServerMiscommunication)
        }
    }

}
