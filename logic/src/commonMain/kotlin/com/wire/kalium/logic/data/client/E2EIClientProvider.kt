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

package com.wire.kalium.logic.data.client

import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.E2EIClient
import com.wire.kalium.logic.E2EIFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.left
import com.wire.kalium.logic.functional.right
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.days

interface E2EIClientProvider {
    suspend fun getE2EIClient(clientId: ClientId? = null, isNewClient: Boolean = false): Either<E2EIFailure, E2EIClient>
    suspend fun nuke()
}

internal class EI2EIClientProviderImpl(
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val mlsClientProvider: MLSClientProvider,
    private val userRepository: UserRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : E2EIClientProvider {

    private var e2EIClient: E2EIClient? = null
    private val defaultE2EIExpiry = 90.days

    private val mutex = Mutex()

    override suspend fun getE2EIClient(
        clientId: ClientId?,
        isNewClient: Boolean
    ): Either<E2EIFailure, E2EIClient> = mutex.withLock {
        withContext(dispatchers.io) {
            val currentClientId =
                clientId ?: currentClientIdProvider().fold({ return@withContext E2EIFailure.GettingE2EIClient(it).left() }, { it })

            return@withContext e2EIClient?.let {
                Either.Right(it)
            } ?: run {
                getSelfUserInfo().flatMap { selfUser ->
                    if (isNewClient) {
                        createNewE2EIClient(currentClientId, selfUser)
                    } else {
                        getE2EIClientFromMLSClient(currentClientId, selfUser)
                    }
                }
            }
        }
    }

    private suspend fun getE2EIClientFromMLSClient(
        currentClientId: ClientId,
        selfUser: SelfUser
    ): Either<E2EIFailure.GettingE2EIClient, E2EIClient> {
        return mlsClientProvider.getMLSClient(currentClientId).fold({
            E2EIFailure.GettingE2EIClient(it).left()
        }, {
            val newE2EIClient = if (it.isE2EIEnabled()) {
                kaliumLogger.w("initial E2EI client for MLS client that already has E2EI enabled")
                it.e2eiNewRotateEnrollment(
                    selfUser.name,
                    selfUser.handle,
                    selfUser.teamId?.value,
                    defaultE2EIExpiry
                )
            } else {
                kaliumLogger.w("initial E2EI client for MLS client without E2EI")
                it.e2eiNewActivationEnrollment(
                    selfUser.name!!,
                    selfUser.handle!!,
                    selfUser.teamId?.value,
                    defaultE2EIExpiry
                )
            }
            e2EIClient = newE2EIClient
            Either.Right(newE2EIClient)
        })
    }

    private suspend fun createNewE2EIClient(
        currentClientId: ClientId,
        selfUser: SelfUser
    ): Either<E2EIFailure.GettingE2EIClient, E2EIClient> {
        kaliumLogger.w("initial E2EI client without MLS client")
        return mlsClientProvider.getCoreCrypto(currentClientId).fold({
            E2EIFailure.GettingE2EIClient(it).left()
        }, {
            val cryptoQualifiedClientId = CryptoQualifiedClientId(
                currentClientId.value,
                selfUser.id.toCrypto()
            )
            val (_, defaultCipherSuite) = mlsClientProvider.getOrFetchMLSConfig().getOrElse { error ->
                return E2EIFailure.GettingE2EIClient(error).left()
            }

            val newE2EIClient = it.newAcmeEnrollment(
                clientId = cryptoQualifiedClientId,
                displayName = selfUser.name!!,
                handle = selfUser.handle!!,
                teamId = selfUser.teamId?.value,
                expiry = defaultE2EIExpiry,
                defaultCipherSuite = defaultCipherSuite.tag.toUShort()
            )
            e2EIClient = newE2EIClient
            Either.Right(newE2EIClient)
        })
    }

    private suspend fun getSelfUserInfo(): Either<E2EIFailure, SelfUser> {
        val selfUser = userRepository.getSelfUser() ?: return E2EIFailure.GettingE2EIClient(StorageFailure.DataNotFound).left()
        return if (selfUser.name == null || selfUser.handle == null)
            E2EIFailure.GettingE2EIClient(StorageFailure.DataNotFound).left()
        else selfUser.right()
    }

    override suspend fun nuke() {
        e2EIClient = null
    }
}
