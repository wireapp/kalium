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

package com.wire.kalium.logic.data.keypackage

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.mls.KeyPackageClaimResult
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapMLSRequest
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageApi
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageCountDTO
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageDTO
import io.ktor.util.encodeBase64

interface KeyPackageRepository {

    /**
     * Claims the key packages for the specified user IDs.
     *
     * Attempts to fetch key packages from self user will be skipped.
     * Attempts to fetch _only_ from self user will result in success even though no key packages were actually claimed.
     *
     * @param userIds The list of user IDs for which to claim key packages.
     * @return An [Either] instance representing the result of the operation. If the operation is successful, it will be [Either.Right]
     * with a [KeyPackageClaimResult] object containing the successfully fetched key packages and the user IDs without key packages
     * available. If the operation fails, it will be [Either.Left] with a [CoreFailure] object indicating the reason for the failure.
     * If **no** KeyPackages are available, [CoreFailure.MissingKeyPackages] will be the cause.
     */
    suspend fun claimKeyPackages(userIds: List<UserId>): Either<CoreFailure, KeyPackageClaimResult>

    suspend fun uploadNewKeyPackages(clientId: ClientId, amount: Int = 100): Either<CoreFailure, Unit>

    suspend fun uploadKeyPackages(clientId: ClientId, keyPackages: List<ByteArray>): Either<CoreFailure, Unit>

    suspend fun replaceKeyPackages(clientId: ClientId, keyPackages: List<ByteArray>): Either<CoreFailure, Unit>

    suspend fun getAvailableKeyPackageCount(clientId: ClientId): Either<NetworkFailure, KeyPackageCountDTO>

    suspend fun validKeyPackageCount(clientId: ClientId): Either<CoreFailure, Int>

}

class KeyPackageDataSource(
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val keyPackageApi: KeyPackageApi,
    private val mlsClientProvider: MLSClientProvider,
    private val selfUserId: UserId,
) : KeyPackageRepository {

    override suspend fun claimKeyPackages(userIds: List<UserId>): Either<CoreFailure, KeyPackageClaimResult> =
        currentClientIdProvider().flatMap { selfClientId ->
            kaliumLogger.d("CFCI -> KeyPackageRepository.claimKeyPackages() | selfClientId: $selfClientId")
            val failedUsers = mutableSetOf<UserId>()
            val claimedKeyPackages = mutableListOf<KeyPackageDTO>()
            userIds.forEach { userId ->
                wrapApiRequest {
                    keyPackageApi.claimKeyPackages(KeyPackageApi.Param.SkipOwnClient(userId.toApi(), selfClientId.value))
                }.fold({ failedUsers.add(userId) }) {
                    if (it.keyPackages.isEmpty() && userId != selfUserId) {
                        kaliumLogger.d("CFCI -> KeyPackageRepository.claimKeyPackages() | failedUsers.add")
                        failedUsers.add(userId)
                    } else {
                        kaliumLogger.d("CFCI -> KeyPackageRepository.claimKeyPackages() | claimedKeyPackages.addAll")
                        claimedKeyPackages.addAll(it.keyPackages)
                    }
                }
            }

            if (claimedKeyPackages.isEmpty() && failedUsers.isNotEmpty()) {
                Either.Left(CoreFailure.MissingKeyPackages(failedUsers))
            } else {
                Either.Right(KeyPackageClaimResult(claimedKeyPackages, failedUsers))
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

    override suspend fun replaceKeyPackages(
        clientId: ClientId,
        keyPackages: List<ByteArray>
    ): Either<CoreFailure, Unit> =
        wrapApiRequest {
            keyPackageApi.replaceKeyPackages(clientId.value, keyPackages.map { it.encodeBase64() })
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
