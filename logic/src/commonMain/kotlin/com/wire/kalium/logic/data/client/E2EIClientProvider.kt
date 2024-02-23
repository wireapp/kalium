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

import com.wire.kalium.cryptography.E2EIClient
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.E2EIFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface E2EIClientProvider {
    suspend fun getE2EIClient(clientId: ClientId? = null, isNewClient: Boolean = false): Either<CoreFailure, E2EIClient>
    suspend fun nuke()
}

internal class EI2EIClientProviderImpl(
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val mlsClientProvider: MLSClientProvider,
    private val userRepository: UserRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : E2EIClientProvider {

    private var e2EIClient: E2EIClient? = null

    private val mutex = Mutex()

    override suspend fun getE2EIClient(clientId: ClientId?, isNewClient: Boolean): Either<CoreFailure, E2EIClient> = mutex.withLock {
        withContext(dispatchers.io) {
            val currentClientId =
                clientId ?: currentClientIdProvider().fold({ return@withContext Either.Left(it) }, { it })

            return@withContext e2EIClient?.let {
                Either.Right(it)
            } ?: run {
                getSelfUserInfo().flatMap { selfUser ->
                    // TODO: use e2eiNewEnrollment for new clients, when CC fix the issues in it
                    mlsClientProvider.getMLSClient(currentClientId).flatMap {
                        val newE2EIClient = if (it.isE2EIEnabled()) {
                            kaliumLogger.e("initial E2EI client for mls client that already has e2ei enabled")
                            it.e2eiNewRotateEnrollment(
                                selfUser.name,
                                selfUser.handle,
                                selfUser.teamId?.value
                            )
                        } else {
                            kaliumLogger.e("initial E2EI client for MLS client without e2ei")
                            it.e2eiNewActivationEnrollment(
                                selfUser.name!!,
                                selfUser.handle!!,
                                selfUser.teamId?.value
                            )
                        }
                        e2EIClient = newE2EIClient
                        Either.Right(newE2EIClient)
                    }
                }
            }
        }
    }

    private suspend fun getSelfUserInfo(): Either<CoreFailure, SelfUser> {
        val selfUser = userRepository.getSelfUser() ?: return Either.Left(CoreFailure.Unknown(NullPointerException()))
        return if (selfUser.name == null || selfUser.handle == null)
            Either.Left(E2EIFailure.Generic(IllegalArgumentException(ERROR_NAME_AND_HANDLE_MUST_NOT_BE_NULL)))
        else Either.Right(selfUser)
    }

    override suspend fun nuke() {
        e2EIClient = null
    }

    companion object {
        const val ERROR_NAME_AND_HANDLE_MUST_NOT_BE_NULL = "name and handle must have a value"
    }
}
