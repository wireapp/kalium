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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.map
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.mls.KeyPackageClaimResult
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.keypackage.ClaimedKeyPackageList
import com.wire.kalium.network.api.authenticated.keypackage.KeyPackageCountDTO
import com.wire.kalium.network.api.authenticated.keypackage.KeyPackageDTO
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.io.encoding.Base64

internal interface KeyPackageRepository {

    /**
     * Claims the key packages for the specified user IDs.
     *
     * Attempts to fetch key packages from self user will be skipped.
     * Attempts to fetch _only_ from self user will result in success even though no key packages were actually claimed.
     *
     * @param userIds The list of user IDs for which to claim key packages.
     * @return An [Either] instance representing the result of the operation. If the operation is successful, it will be [Either.Right]
     * with a [KeyPackageClaimResult] object containing the successfully fetched key packages,
     * the user IDs that genuinely have no key packages ([KeyPackageClaimResult.usersWithoutKeyPackages]),
     * and the user IDs whose backend was unreachable during claiming ([KeyPackageClaimResult.usersWithUnreachableBackend]).
     * If the operation fails entirely (e.g. self client ID unavailable), it will be [Either.Left] with a [CoreFailure].
     */
    suspend fun claimKeyPackages(
        userIds: List<UserId>,
        cipherSuite: CipherSuite
    ): Either<CoreFailure, KeyPackageClaimResult>

    suspend fun uploadNewKeyPackages(
        mlsContext: MlsCoreCryptoContext,
        clientId: ClientId,
        amount: Int = 100
    ): Either<CoreFailure, Unit>

    suspend fun uploadKeyPackages(clientId: ClientId, keyPackages: List<ByteArray>): Either<CoreFailure, Unit>

    suspend fun replaceKeyPackages(clientId: ClientId, keyPackages: List<ByteArray>, cipherSuite: CipherSuite): Either<CoreFailure, Unit>

    suspend fun getAvailableKeyPackageCount(clientId: ClientId, cipherSuite: CipherSuite): Either<NetworkFailure, KeyPackageCountDTO>

    suspend fun validKeyPackageCount(
        mlsContext: MlsCoreCryptoContext,
        clientId: ClientId
    ): Either<CoreFailure, Int>
}

internal class KeyPackageDataSource(
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val keyPackageApi: KeyPackageApi,
    private val selfUserId: UserId,
    private val maxConcurrentClaims: Int = DEFAULT_MAX_CONCURRENT_CLAIMS,
) : KeyPackageRepository {

    init {
        require(maxConcurrentClaims > 0) { "maxConcurrentClaims must be greater than zero" }
    }

    override suspend fun claimKeyPackages(
        userIds: List<UserId>,
        cipherSuite: CipherSuite
    ): Either<CoreFailure, KeyPackageClaimResult> =
        currentClientIdProvider().flatMap { selfClientId ->
            userIds.chunked(maxConcurrentClaims)
                .foldToEitherWhileRight(KeyPackageClaims()) { batch, accumulatedClaims ->
                    claimBatch(batch, selfClientId, cipherSuite)
                        .foldToEitherWhileRight(accumulatedClaims, ::accumulateClaim)
                }
                .map(KeyPackageClaims::toResult)
        }

    private suspend fun claimBatch(
        userIds: List<UserId>,
        selfClientId: ClientId,
        cipherSuite: CipherSuite,
    ): List<UserKeyPackageClaim> = coroutineScope {
        userIds.map { userId ->
            async {
                UserKeyPackageClaim(
                    userId = userId,
                    result = wrapApiRequest {
                        keyPackageApi.claimKeyPackages(
                            KeyPackageApi.Param.SkipOwnClient(
                                userId.toApi(),
                                selfClientId.value,
                                cipherSuite = cipherSuite.tag
                            )
                        )
                    }
                )
            }
        }.awaitAll()
    }

    private fun accumulateClaim(
        claim: UserKeyPackageClaim,
        accumulatedClaims: KeyPackageClaims,
    ): Either<CoreFailure, KeyPackageClaims> = claim.result.fold({ failure ->
        when (failure) {
            is NetworkFailure.NoNetworkConnection,
            is NetworkFailure.ProxyError -> Either.Left(failure)
            else -> {
                accumulatedClaims.usersWithUnreachableBackend.add(claim.userId)
                Either.Right(accumulatedClaims)
            }
        }
    }) { claimedKeyPackages ->
        if (claimedKeyPackages.keyPackages.isEmpty() && claim.userId != selfUserId) {
            accumulatedClaims.usersWithoutKeyPackages.add(claim.userId)
        } else {
            accumulatedClaims.successfullyFetchedKeyPackages.addAll(claimedKeyPackages.keyPackages)
        }
        Either.Right(accumulatedClaims)
    }

    override suspend fun uploadNewKeyPackages(
        mlsContext: MlsCoreCryptoContext,
        clientId: ClientId,
        amount: Int
    ): Either<CoreFailure, Unit> =
        wrapMLSRequest {
            mlsContext.generateKeyPackages(amount)
        }.flatMap { keyPackages ->
            wrapApiRequest {
                keyPackageApi.uploadKeyPackages(clientId.value, keyPackages.map { Base64.encode(it) })
            }
        }

    override suspend fun uploadKeyPackages(
        clientId: ClientId,
        keyPackages: List<ByteArray>
    ): Either<CoreFailure, Unit> =
        wrapApiRequest {
            keyPackageApi.uploadKeyPackages(clientId.value, keyPackages.map { Base64.encode(it) })
        }

    override suspend fun replaceKeyPackages(
        clientId: ClientId,
        keyPackages: List<ByteArray>,
        cipherSuite: CipherSuite
    ): Either<CoreFailure, Unit> =
        wrapApiRequest {
            keyPackageApi.replaceKeyPackages(clientId.value, keyPackages.map { Base64.encode(it) }, cipherSuite.tag)
        }

    override suspend fun validKeyPackageCount(
        mlsContext: MlsCoreCryptoContext,
        clientId: ClientId
    ): Either<CoreFailure, Int> =
        wrapMLSRequest {
            mlsContext.validKeyPackageCount().toInt()
        }

    override suspend fun getAvailableKeyPackageCount(
        clientId: ClientId,
        cipherSuite: CipherSuite
    ): Either<NetworkFailure, KeyPackageCountDTO> =
        wrapApiRequest {
            keyPackageApi.getAvailableKeyPackageCount(clientId.value, cipherSuite.tag)
        }

    private companion object {
        const val DEFAULT_MAX_CONCURRENT_CLAIMS = 4
    }
}

private data class UserKeyPackageClaim(
    val userId: UserId,
    val result: Either<NetworkFailure, ClaimedKeyPackageList>,
)

private class KeyPackageClaims {
    val successfullyFetchedKeyPackages = mutableListOf<KeyPackageDTO>()
    val usersWithoutKeyPackages = mutableSetOf<UserId>()
    val usersWithUnreachableBackend = mutableSetOf<UserId>()

    fun toResult() = KeyPackageClaimResult(
        successfullyFetchedKeyPackages = successfullyFetchedKeyPackages.toList(),
        usersWithoutKeyPackages = usersWithoutKeyPackages.toSet(),
        usersWithUnreachableBackend = usersWithUnreachableBackend.toSet(),
    )
}
