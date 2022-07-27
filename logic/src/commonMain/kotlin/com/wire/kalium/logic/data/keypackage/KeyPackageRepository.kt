package com.wire.kalium.logic.data.keypackage

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.keypackage.KeyPackageApi
import com.wire.kalium.network.api.keypackage.KeyPackageCountDTO
import com.wire.kalium.network.api.keypackage.KeyPackageDTO
import com.wire.kalium.persistence.dao.MetadataDAO
import io.ktor.util.encodeBase64
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Instant

interface KeyPackageRepository {

    suspend fun claimKeyPackages(userIds: List<UserId>): Either<CoreFailure, List<KeyPackageDTO>>

    suspend fun uploadNewKeyPackages(clientId: ClientId, amount: Int = 100): Either<CoreFailure, Unit>

    suspend fun getAvailableKeyPackageCount(clientId: ClientId): Either<NetworkFailure, KeyPackageCountDTO>

    suspend fun lastKeyPackageCountCheck(): Either<StorageFailure, Instant>

    suspend fun updateLastKeyPackageCountCheck(timestamp: Instant): Either<StorageFailure, Unit>

}

internal const val LAST_KEY_PACKAGE_COUNT_CHECK = "LAST_KEY_PACKAGE_COUNT_CHECK"

class KeyPackageDataSource(
    private val clientRepository: ClientRepository,
    private val keyPackageApi: KeyPackageApi,
    private val mlsClientProvider: MLSClientProvider,
    private val metadataDAO: MetadataDAO,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
) : KeyPackageRepository {

    override suspend fun claimKeyPackages(userIds: List<UserId>): Either<CoreFailure, List<KeyPackageDTO>> =
        clientRepository.currentClientId().flatMap { selfClientId ->
            userIds.map { userId ->
                wrapApiRequest {
                    keyPackageApi.claimKeyPackages(
                        KeyPackageApi.Param.SkipOwnClient(idMapper.toApiModel(userId), selfClientId.value)
                    )
                }.map {
                    it.keyPackages
                }
            }.foldToEitherWhileRight(emptyList()) { item, acc ->
                item.flatMap { Either.Right(acc + it) }
            }
        }

    override suspend fun uploadNewKeyPackages(clientId: ClientId, amount: Int): Either<CoreFailure, Unit> =
        mlsClientProvider.getMLSClient(clientId).flatMap { mlsClient ->
            wrapApiRequest {
                keyPackageApi.uploadKeyPackages(clientId.value, mlsClient.generateKeyPackages(amount).map { it.encodeBase64() })
            }
        }

    override suspend fun getAvailableKeyPackageCount(clientId: ClientId): Either<NetworkFailure, KeyPackageCountDTO> =
        wrapApiRequest {
            keyPackageApi.getAvailableKeyPackageCount(clientId.value)
        }

    override suspend fun lastKeyPackageCountCheck(): Either<StorageFailure, Instant> =
        wrapStorageRequest {
            metadataDAO.observerValueByKey(LAST_KEY_PACKAGE_COUNT_CHECK).firstOrNull()?.let { Instant.parse(it) } ?: Instant.DISTANT_PAST
        }

    override suspend fun updateLastKeyPackageCountCheck(timestamp: Instant): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            metadataDAO.insertValue(timestamp.toString(), LAST_KEY_PACKAGE_COUNT_CHECK)
        }

}
