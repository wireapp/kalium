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
package com.wire.kalium.logic.feature.client

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.ProteusFailure
import com.wire.kalium.common.error.wrapProteusRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.mapLeft
import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.ProteusCoreCryptoContext
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.user.UserId

/**
 * Retrieves the Proteus-specific fingerprint of a client.
 * If no session exists for the client, a new session is established.
 * @param userId The user id of the client.
 * @param clientId The client id of the client.
 * @return [Result.Success] if the fingerprint was retrieved successfully.
 * [Result.Failure] if the fingerprint could not be retrieved.
 */
interface ClientFingerprintUseCase {
    suspend operator fun invoke(userId: UserId, clientId: ClientId): Result
}

class ClientFingerprintUseCaseImpl internal constructor(
    private val transactionProvider: CryptoTransactionProvider,
    private val prekeyRepository: PreKeyRepository
) : ClientFingerprintUseCase {
    override suspend operator fun invoke(userId: UserId, clientId: ClientId): Result =
        transactionProvider.proteusTransaction("ClientFingerprint") { proteusContext ->
            wrapProteusRequest {
                proteusContext.remoteFingerPrint(CryptoSessionId(userId.toCrypto(), CryptoClientId(clientId.value)))
            }
                .mapLeft { it as CoreFailure }
                .flatMapLeft {
                    when (it) {
                        is ProteusFailure -> onProteusFailure(it, userId, clientId, proteusContext)
                        else -> it.left()
                    }
                }
        }
            .fold(Result::Failure, Result::Success)

    private suspend fun onProteusFailure(
        proteusFailure: ProteusFailure,
        userId: UserId,
        clientId: ClientId,
        proteusContext: ProteusCoreCryptoContext
    ): Either<CoreFailure, ByteArray> =
        when (proteusFailure.proteusException.code) {
            ProteusException.Code.SESSION_NOT_FOUND -> onSessionNotFound(userId, clientId, proteusContext)
            else -> Either.Left(proteusFailure)
        }

    private suspend fun onSessionNotFound(
        userId: UserId,
        clientId: ClientId,
        proteusContext: ProteusCoreCryptoContext
    ): Either<CoreFailure, ByteArray> {
        return prekeyRepository.establishSessions(proteusContext, mapOf(userId to listOf(clientId))).fold(
            { error -> Either.Left(error) },
            { _ ->
                wrapProteusRequest {
                    proteusContext.remoteFingerPrint(CryptoSessionId(userId.toCrypto(), CryptoClientId(clientId.value)))
                }
            }
        )
    }
}

sealed interface Result {
    data class Success(val fingerprint: ByteArray) : Result {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Success

            return fingerprint.contentEquals(other.fingerprint)
        }

        override fun hashCode(): Int {
            return fingerprint.contentHashCode()
        }
    }

    data class Failure(val error: CoreFailure) : Result
}
