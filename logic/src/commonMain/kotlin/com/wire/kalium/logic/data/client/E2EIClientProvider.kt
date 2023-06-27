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
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

interface E2EClientProvider {
    suspend fun getE2EIClient(clientId: ClientId? = null): Either<CoreFailure, E2EIClient>
}

class E2EIClientProviderImpl(
    private val userId: UserId,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val mlsClientProvider: MLSClientProvider,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : E2EClientProvider {

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
                        val newE2EIClient = it.newAcmeEnrollment(
                            e2eiClientId,
                            selfUser.first,
                            selfUser.second
                        )
                        e2EIClient = newE2EIClient
                        Either.Right(newE2EIClient)
                    }
                }
            }

        }

    private suspend fun getSelfUserInfo(): Either<CoreFailure, Pair<String, String>> {
        /*val selfUser = selfUserUseCase().first()
        return if (selfUser.name == null || selfUser.handle == null)
            Either.Left(E2EIFailure(IllegalArgumentException(ERROR_NAME_AND_HANDLE_MUST_NOT_BE_NULL)))
        else*/
        // todo: Mojtaba remove hardcoded values later
        return Either.Right(Pair("Mojtaba Chenani", "mojtaba_wire"))
    }

    companion object {
        var hasCertificate = false
        const val ERROR_NAME_AND_HANDLE_MUST_NOT_BE_NULL = "name and handle must have a value"
    }
}
