package com.wire.kalium.logic.data.prekey

import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.ProteusFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.prekey.remote.PreKeyRemoteDataSource
import com.wire.kalium.logic.data.prekey.remote.PreKeyRemoteRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapCryptoRequest

interface PreKeyRepository {
    suspend fun preKeysOfClientsByQualifiedUsers(
        qualifiedIdsMap: Map<UserId, List<ClientId>>
    ): Either<NetworkFailure, List<QualifiedUserPreKeyInfo>>

    suspend fun generateNewPreKeys(firstKeyId: Int, keysCount: Int): Either<ProteusFailure, List<PreKeyCrypto>>
    fun generateNewLastKey(): Either<ProteusFailure, PreKeyCrypto>
}

class PreKeyDataSource(
    private val preKeyRemoteRepository: PreKeyRemoteRepository,
    private val proteusClient: ProteusClient
) : PreKeyRepository {
    override suspend fun preKeysOfClientsByQualifiedUsers(
        qualifiedIdsMap: Map<UserId, List<ClientId>>
    ): Either<NetworkFailure, List<QualifiedUserPreKeyInfo>> = preKeyRemoteRepository.preKeysForMultipleQualifiedUsers(qualifiedIdsMap)

    override suspend fun generateNewPreKeys(firstKeyId: Int, keysCount: Int): Either<ProteusFailure, List<PreKeyCrypto>> =
        wrapCryptoRequest { proteusClient.newPreKeys(firstKeyId, keysCount) }

    override fun generateNewLastKey(): Either<ProteusFailure, PreKeyCrypto> =
        wrapCryptoRequest { proteusClient.newLastPreKey() }
}
