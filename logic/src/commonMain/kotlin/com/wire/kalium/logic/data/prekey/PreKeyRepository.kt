/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.data.prekey

import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.cryptography.createSessions
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.ProteusFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.data.message.CryptoSessionMapper
import com.wire.kalium.logic.data.message.CryptoSessionMapperImpl
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapProteusRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.prekey.DomainToUserIdToClientsToPreKeyMap
import com.wire.kalium.network.api.base.authenticated.prekey.ListPrekeysResponse
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyApi
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.PrekeyDAO
import com.wire.kalium.persistence.dao.client.ClientDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

@Suppress("TooManyFunctions")
interface PreKeyRepository {
    /**
     * Fetches the IDs of the prekeys currently available on the backend.
     * @see uploadNewPrekeyBatch
     */
    suspend fun fetchRemotelyAvailablePrekeys(): Either<CoreFailure, List<Int>>

    /**
     * Uploads a batch of prekeys to the backend, so they become available
     * for other clients to start sessions with this client.
     * @see fetchRemotelyAvailablePrekeys
     */
    suspend fun uploadNewPrekeyBatch(batch: List<PreKeyCrypto>): Either<CoreFailure, Unit>

    /**
     * Generate prekeys to be uploaded to the backend and shared with other clients in
     * order to initialise a new conversation with this client.
     *
     * As these are consumed, we should keep uploading new prekeys to the backend.
     * For this reason, these can be called "rolling" prekeys, in an attempt to separate them
     * from the "last resort" prekey, which is described in [generateNewLastResortKey].
     * @see generateNewLastResortKey
     */
    suspend fun generateNewPreKeys(firstKeyId: Int, keysCount: Int): Either<CoreFailure, List<PreKeyCrypto>>

    /**
     * Observes the last pre-key check instant.
     *
     * @return A [Flow] of [Instant] objects representing the last pre-key upload instant.
     * It emits `null` if no pre-key upload has occurred.
     */
    suspend fun lastPreKeyRefillCheckInstantFlow(): Flow<Instant?>

    /**
     * Sets the last prekey refill check date.
     *
     * @param instant The instant representing the date and time of the last prekey upload.
     * @return Either a [StorageFailure] if the operation fails, or [Unit] if successful.
     */
    suspend fun setLastPreKeyRefillCheckInstant(instant: Instant): Either<StorageFailure, Unit>

    /**
     * Also known as "last prekey", it's the prekey that the backend will
     * share with other clients when it runs out of prekeys for this client.
     * For "rolling" prekeys, see [generateNewPreKeys].
     * @see generateNewPreKeys
     */
    suspend fun generateNewLastResortKey(): Either<ProteusFailure, PreKeyCrypto>
    suspend fun getLocalFingerprint(): Either<CoreFailure, ByteArray>

    /**
     * Returns the ID of the most recent "rolling" prekey that was generated.
     * @see generateNewPreKeys
     */
    suspend fun mostRecentPreKeyId(): Either<StorageFailure, Int>

    /**
     * Updates the ID of the most recent "rolling" prekey that was generated.
     * @see generateNewPreKeys
     * @see forceInsertMostRecentPreKeyId
     */
    suspend fun updateMostRecentPreKeyId(newId: Int): Either<StorageFailure, Unit>

    /**
     * Forces the insert of the ID of the most recent "rolling" prekey that was generated.
     * Useful
     * @see updateMostRecentPreKeyId
     */
    suspend fun forceInsertMostRecentPreKeyId(newId: Int): Either<StorageFailure, Unit>
    suspend fun establishSessions(
        missingContactClients: Map<UserId, List<ClientId>>
    ): Either<CoreFailure, UsersWithoutSessions>

    suspend fun getFingerprintForPreKey(preKeyCrypto: PreKeyCrypto): Either<CoreFailure, ByteArray>
}

@Suppress("LongParameterList", "TooManyFunctions")
class PreKeyDataSource(
    private val preKeyApi: PreKeyApi,
    private val proteusClientProvider: ProteusClientProvider,
    private val provideCurrentClientId: CurrentClientIdProvider,
    private val prekeyDAO: PrekeyDAO,
    private val clientDAO: ClientDAO,
    private val metadataDAO: MetadataDAO,
    private val preKeyListMapper: PreKeyListMapper = MapperProvider.preKeyListMapper(),
    private val preKeyMapper: PreKeyMapper = MapperProvider.preyKeyMapper()
) : PreKeyRepository, CryptoSessionMapper by CryptoSessionMapperImpl(MapperProvider.preyKeyMapper()) {

    override suspend fun fetchRemotelyAvailablePrekeys(): Either<CoreFailure, List<Int>> =
        provideCurrentClientId().flatMap { clientId ->
            wrapApiRequest {
                preKeyApi.getClientAvailablePrekeys(clientId.value)
            }
        }

    override suspend fun uploadNewPrekeyBatch(batch: List<PreKeyCrypto>): Either<CoreFailure, Unit> =
        provideCurrentClientId().flatMap { clientId ->
            val preKeyDTOs = batch.map(preKeyMapper::toPreKeyDTO)
            wrapApiRequest {
                preKeyApi.uploadNewPrekeys(clientId.value, preKeyDTOs)
            }
        }

    override suspend fun generateNewPreKeys(
        firstKeyId: Int,
        keysCount: Int
    ): Either<ProteusFailure, List<PreKeyCrypto>> =
        wrapProteusRequest { proteusClientProvider.getOrCreate().newPreKeys(firstKeyId, keysCount) }.onSuccess {
            kaliumLogger.i(
                """Generating PreKeys: {"success":true,"firstKeyId":$firstKeyId,"$keysCount":$keysCount}"""
            )
        }.onFailure {
            kaliumLogger.i(
                """Generating PreKeys: {"success":false,"firstKeyId":$firstKeyId, "$keysCount":$keysCount}"""
            )
        }

    override suspend fun generateNewLastResortKey(): Either<ProteusFailure, PreKeyCrypto> =
        wrapProteusRequest {
            proteusClientProvider.getOrCreate().newLastResortPreKey()
        }

    override suspend fun getLocalFingerprint(): Either<CoreFailure, ByteArray> =
        proteusClientProvider.getOrError().flatMap { proteusClient ->
            wrapProteusRequest {
                proteusClient.getLocalFingerprint()
            }
        }

    override suspend fun mostRecentPreKeyId(): Either<StorageFailure, Int> = wrapStorageRequest {
        prekeyDAO.mostRecentPreKeyId()
    }

    override suspend fun updateMostRecentPreKeyId(newId: Int): Either<StorageFailure, Unit> = wrapStorageRequest {
        prekeyDAO.updateMostRecentPreKeyId(newId)
    }

    override suspend fun forceInsertMostRecentPreKeyId(newId: Int): Either<StorageFailure, Unit> = wrapStorageRequest {
        prekeyDAO.forceInsertMostRecentPreKeyId(newId)
    }

    override suspend fun lastPreKeyRefillCheckInstantFlow(): Flow<Instant?> =
        metadataDAO.valueByKeyFlow(PREKEY_REFILL_INSTANT_KEY).map { instant ->
            instant?.let { Instant.parse(it) }
        }

    override suspend fun setLastPreKeyRefillCheckInstant(instant: Instant): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            metadataDAO.insertValue(instant.toString(), PREKEY_REFILL_INSTANT_KEY)
        }

    override suspend fun establishSessions(
        missingContactClients: Map<UserId, List<ClientId>>
    ): Either<CoreFailure, UsersWithoutSessions> {
        if (missingContactClients.isEmpty()) {
            return Either.Right(UsersWithoutSessions.EMPTY)
        }

        return preKeysOfClientsByQualifiedUsers(missingContactClients)
            .flatMap { listUserPrekeysResponse ->
                establishProteusSessions(listUserPrekeysResponse.qualifiedUserClientPrekeys)
                    .flatMap {
                        Either.Right(
                            preKeyListMapper.fromListPrekeyResponseToUsersWithoutSessions(
                                listUserPrekeysResponse
                            )
                        )
                    }
            }
    }

    override suspend fun getFingerprintForPreKey(preKeyCrypto: PreKeyCrypto): Either<CoreFailure, ByteArray> =
        proteusClientProvider.getOrError().flatMap { proteusClient ->
            wrapProteusRequest {
                proteusClient.getFingerprintFromPreKey(preKeyCrypto)
            }
        }

    internal suspend fun preKeysOfClientsByQualifiedUsers(
        qualifiedIdsMap: Map<UserId, List<ClientId>>
    ): Either<NetworkFailure, ListPrekeysResponse> = wrapApiRequest {
        preKeyApi.getUsersPreKey(preKeyListMapper.toRemoteClientPreKeyInfoTo(qualifiedIdsMap))
    }

    private suspend fun establishProteusSessions(
        preKeyInfoList: DomainToUserIdToClientsToPreKeyMap
    ): Either<CoreFailure, Unit> =
        proteusClientProvider.getOrError()
            .flatMap { proteusClient ->
                val (valid, invalid) = getMapOfSessionIdsToPreKeysAndMarkNullClientsAsInvalid(preKeyInfoList)
                wrapProteusRequest {
                    proteusClient.createSessions(valid)
                }.also {
                    wrapStorageRequest {
                        clientDAO.tryMarkInvalid(invalid)
                    }
                }
            }

    private companion object {
        const val PREKEY_REFILL_INSTANT_KEY = "last_prekey_refill_instant"
    }
}
