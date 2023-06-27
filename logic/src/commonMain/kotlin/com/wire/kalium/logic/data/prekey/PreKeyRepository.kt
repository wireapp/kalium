/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.ProteusFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.ProteusClientProvider
import com.wire.kalium.logic.feature.message.CryptoSessionMapper
import com.wire.kalium.logic.feature.message.CryptoSessionMapperImpl
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapProteusRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.prekey.DomainToUserIdToClientsToPreKeyMap
import com.wire.kalium.network.api.base.authenticated.prekey.ListPrekeysResponse
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyApi
import com.wire.kalium.persistence.dao.PrekeyDAO
import com.wire.kalium.persistence.dao.client.ClientDAO

interface PreKeyRepository {
    suspend fun generateNewPreKeys(firstKeyId: Int, keysCount: Int): Either<CoreFailure, List<PreKeyCrypto>>
    suspend fun generateNewLastKey(): Either<ProteusFailure, PreKeyCrypto>
    suspend fun getLocalFingerprint(): Either<CoreFailure, ByteArray>
    suspend fun lastPreKeyId(): Either<StorageFailure, Int>
    suspend fun updateOTRLastPreKeyId(newId: Int): Either<StorageFailure, Unit>
    suspend fun forceInsertPrekeyId(newId: Int): Either<StorageFailure, Unit>
    suspend fun establishSessions(
        missingContactClients: Map<UserId, List<ClientId>>
    ): Either<CoreFailure, UsersWithoutSessions>
}

class PreKeyDataSource(
    private val preKeyApi: PreKeyApi,
    private val proteusClientProvider: ProteusClientProvider,
    private val prekeyDAO: PrekeyDAO,
    private val clientDAO: ClientDAO,
    private val preKeyListMapper: PreKeyListMapper = MapperProvider.preKeyListMapper()
) : PreKeyRepository, CryptoSessionMapper by CryptoSessionMapperImpl(MapperProvider.preyKeyMapper()) {
    override suspend fun generateNewPreKeys(
        firstKeyId: Int,
        keysCount: Int
    ): Either<ProteusFailure, List<PreKeyCrypto>> =
        wrapProteusRequest { proteusClientProvider.getOrCreate().newPreKeys(firstKeyId, keysCount) }

    override suspend fun generateNewLastKey(): Either<ProteusFailure, PreKeyCrypto> =
        wrapProteusRequest {
            proteusClientProvider.getOrCreate().newLastPreKey()
        }

    override suspend fun getLocalFingerprint(): Either<CoreFailure, ByteArray> =
        proteusClientProvider.getOrError().flatMap { proteusClient ->
            wrapProteusRequest {
                proteusClient.getLocalFingerprint()
            }
        }

    override suspend fun lastPreKeyId(): Either<StorageFailure, Int> = wrapStorageRequest {
        prekeyDAO.lastOTRPrekeyId()
    }

    override suspend fun updateOTRLastPreKeyId(newId: Int): Either<StorageFailure, Unit> = wrapStorageRequest {
        prekeyDAO.updateOTRLastPrekeyId(newId)
    }

    override suspend fun forceInsertPrekeyId(newId: Int): Either<StorageFailure, Unit> = wrapStorageRequest {
        prekeyDAO.forceInsertOTRLastPrekeyId(newId)
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
                        Either.Right(preKeyListMapper.fromListPrekeyResponseToUsersWithoutSessions(listUserPrekeysResponse))
                    }
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
}
