package com.wire.kalium.logic.data.keypackage

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.keypackage.ClaimedKeyPackageList
import com.wire.kalium.network.api.keypackage.KeyPackageApi
import io.ktor.util.encodeBase64

interface KeyPackageRepository {

    suspend fun claimKeyPackages(userId: UserId): Either<NetworkFailure, ClaimedKeyPackageList>

    suspend fun uploadNewKeyPackages(clientId: ClientId, amount: Int = 100): Either<CoreFailure, Unit>

    suspend fun getAvailableKeyPackageCount(clientId: ClientId): Either<NetworkFailure, Int>

}

class KeyPackageDataSource(
    private val keyPackageApi: KeyPackageApi,
    private val mlsClientProvider: MLSClientProvider,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    ) : KeyPackageRepository {

    override suspend fun claimKeyPackages(userId: UserId): Either<NetworkFailure, ClaimedKeyPackageList> =
        wrapApiRequest {
            keyPackageApi.claimKeyPackages(idMapper.toApiModel(userId))
        }

    override suspend fun uploadNewKeyPackages(clientId: ClientId, amount: Int): Either<CoreFailure, Unit> = suspending {
        mlsClientProvider.getMLSClient(clientId).flatMap { mlsClient ->
            wrapApiRequest {
                keyPackageApi.uploadKeyPackages(clientId.value, mlsClient.generateKeyPackages(amount).map { it.encodeBase64() })
            }
        }
    }

    override suspend fun getAvailableKeyPackageCount(clientId: ClientId): Either<NetworkFailure, Int> =
        wrapApiRequest {
            keyPackageApi.getAvailableKeyPackageCount(clientId.value)
        }

}
