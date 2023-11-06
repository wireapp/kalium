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

package com.wire.kalium.logic.data.client

import com.wire.kalium.cryptography.CryptoQualifiedID
import com.wire.kalium.cryptography.E2EIClient
import com.wire.kalium.cryptography.E2EIQualifiedClientId
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.E2EIFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

interface E2EIClientProvider {
    suspend fun getE2EIClient(clientId: ClientId? = null): Either<CoreFailure, E2EIClient>
}

internal class EI2EIClientProviderImpl(
    private val userId: UserId,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val mlsClientProvider: MLSClientProvider,
    private val userRepository: UserRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : E2EIClientProvider {

    private var e2EIClient: E2EIClient? = null

    override suspend fun getE2EIClient(clientId: ClientId?): Either<CoreFailure, E2EIClient> =
        withContext(dispatchers.io) {
            val currentClientId =
                clientId ?: currentClientIdProvider().fold({ return@withContext Either.Left(it) }, { it })
            val e2eiClientId = E2EIQualifiedClientId(
                currentClientId.value,
                CryptoQualifiedID(value = userId.value, domain = userId.domain)
            )

            return@withContext e2EIClient?.let {
                Either.Right(it)
            } ?: run {
                getSelfUserInfo().flatMap { selfUser ->
                    mlsClientProvider.getMLSClient(currentClientId).flatMap {
                        val newE2EIClient = if (it.isE2EIEnabled()) {
                            kaliumLogger.e("initial E2EI client for MLS client without e2ei")
                            it.e2eiNewRotateEnrollment(
                                e2eiClientId,
                                selfUser.first,
                                selfUser.second
                            )
                        } else {
                            kaliumLogger.e("initial E2EI client for mls client that already has e2ei enabled")
                            it.e2eiNewActivationEnrollment(
                                e2eiClientId,
                                selfUser.first,
                                selfUser.second
                            )
                        }
                        e2EIClient = newE2EIClient
                        Either.Right(newE2EIClient)
                    }
                }
            }

        }

    private suspend fun getSelfUserInfo(): Either<CoreFailure, Pair<String, String>> {
        val selfUser = userRepository.getSelfUser() ?: return Either.Left(CoreFailure.Unknown(NullPointerException()))
        return if (selfUser.name == null || selfUser.handle == null)
            Either.Left(E2EIFailure(IllegalArgumentException(ERROR_NAME_AND_HANDLE_MUST_NOT_BE_NULL)))
        else Either.Right(Pair(selfUser.name, selfUser.handle))
    }

    companion object {
        const val ERROR_NAME_AND_HANDLE_MUST_NOT_BE_NULL = "name and handle must have a value"
    }
}
