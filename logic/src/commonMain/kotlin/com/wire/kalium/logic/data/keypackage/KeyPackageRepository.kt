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

package com.wire.kalium.logic.data.keypackage

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapMLSRequest
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageApi
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageCountDTO
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageDTO
import io.ktor.util.encodeBase64

interface KeyPackageRepository {

    suspend fun claimKeyPackages(userIds: List<UserId>): Either<CoreFailure, List<KeyPackageDTO>>

    suspend fun uploadNewKeyPackages(clientId: ClientId, amount: Int = 100): Either<CoreFailure, Unit>

    suspend fun uploadKeyPackages(clientId: ClientId, keyPackages: List<ByteArray>): Either<CoreFailure, Unit>

    suspend fun deleteKeyPackages(clientId: ClientId, keyPackages: List<ByteArray>): Either<CoreFailure, Unit>

    suspend fun getAvailableKeyPackageCount(clientId: ClientId): Either<NetworkFailure, KeyPackageCountDTO>

    suspend fun validKeyPackageCount(clientId: ClientId): Either<CoreFailure, Int>

}

class KeyPackageDataSource(
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val keyPackageApi: KeyPackageApi,
    private val mlsClientProvider: MLSClientProvider,
    private val selfUserId: UserId,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
) : KeyPackageRepository {

    // todo(ym) make it federation aware and  make it to return list of failed users, aka accumulate responses.
    override suspend fun claimKeyPackages(userIds: List<UserId>): Either<CoreFailure, List<KeyPackageDTO>> =
        currentClientIdProvider().flatMap { selfClientId ->
            userIds.map { userId ->
                wrapApiRequest {
                    keyPackageApi.claimKeyPackages(
                        KeyPackageApi.Param.SkipOwnClient(userId.toApi(), selfClientId.value)
                    )
                }.flatMap {
                    if (it.keyPackages.isEmpty() && userId != selfUserId) {
                        Either.Left(CoreFailure.NoKeyPackagesAvailable(userId)) // todo, handle similar to unreachable with list of users
                    } else {
                        Either.Right(it.keyPackages)
                    }
                }
            }.foldToEitherWhileRight(emptyList()) { item, acc ->
                item.flatMap { Either.Right(acc + it) }
            }
        }

    override suspend fun uploadNewKeyPackages(clientId: ClientId, amount: Int): Either<CoreFailure, Unit> =
        mlsClientProvider.getMLSClient(clientId).flatMap { mlsClient ->
            wrapMLSRequest {
                mlsClient.generateKeyPackages(amount)
            }.flatMap { keyPackages ->
                wrapApiRequest {
                    keyPackageApi.uploadKeyPackages(clientId.value, keyPackages.map { it.encodeBase64() })
                }
            }
        }

    override suspend fun uploadKeyPackages(
        clientId: ClientId,
        keyPackages: List<ByteArray>
    ): Either<CoreFailure, Unit> =
        wrapApiRequest {
            keyPackageApi.uploadKeyPackages(clientId.value, keyPackages.map { it.encodeBase64() })
        }

    override suspend fun deleteKeyPackages(
        clientId: ClientId,
        keyPackages: List<ByteArray>
    ): Either<CoreFailure, Unit> =
        wrapApiRequest {
            keyPackageApi.deleteKeyPackages(clientId.value, keyPackages.map { it.encodeBase64() })
        }

    override suspend fun validKeyPackageCount(clientId: ClientId): Either<CoreFailure, Int> =
        mlsClientProvider.getMLSClient(clientId).flatMap { mlsClient ->
            wrapMLSRequest {
                mlsClient.validKeyPackageCount().toInt()
            }
        }

    override suspend fun getAvailableKeyPackageCount(clientId: ClientId): Either<NetworkFailure, KeyPackageCountDTO> =
        wrapApiRequest {
            keyPackageApi.getAvailableKeyPackageCount(clientId.value)
        }
}
