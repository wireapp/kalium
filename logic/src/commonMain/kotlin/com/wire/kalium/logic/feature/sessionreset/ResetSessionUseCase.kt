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

package com.wire.kalium.logic.feature.sessionreset

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.feature.message.SessionResetSender
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * If the Cryptobox session between two users is broken it can sometimes be repaired by calling this use case
 */
interface ResetSessionUseCase {
    suspend operator fun invoke(conversationId: ConversationId, userId: UserId, clientId: ClientId): ResetSessionResult
}

internal class ResetSessionUseCaseImpl internal constructor(
    private val proteusClientProvider: ProteusClientProvider,
    private val sessionResetSender: SessionResetSender,
    private val messageRepository: MessageRepository,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : ResetSessionUseCase {
    override suspend operator fun invoke(
        conversationId: ConversationId,
        userId: UserId,
        clientId: ClientId
    ): ResetSessionResult = withContext(dispatchers.io) {
        return@withContext proteusClientProvider.getOrError().fold({
            kaliumLogger.e("Failed to get proteus client for session reset $it")
            return@fold ResetSessionResult.Failure(it)
        }, { proteusClient ->
            val cryptoUserID = idMapper.toCryptoQualifiedIDId(userId)
            val cryptoSessionId = CryptoSessionId(
                userId = cryptoUserID,
                cryptoClientId = CryptoClientId(clientId.value)
            )
            proteusClient.deleteSession(cryptoSessionId)
            // TODO("Update device verified state to false once implemented")
            return@fold sessionResetSender(
                conversationId = conversationId,
                userId = userId,
                clientId = clientId
            ).flatMap {
                kaliumLogger.e("Successfully sent session reset message")
                messageRepository.markMessagesAsDecryptionResolved(
                    conversationId,
                    userId,
                    clientId
                )
            }.fold(
                {
                    kaliumLogger.e("Failed to mark decryption error as resolved")
                    ResetSessionResult.Failure(it)
                },
                {
                    kaliumLogger.e("Successfully marked decryption error as resolved")
                    ResetSessionResult.Success
                }
            )

        })
    }
}

sealed class ResetSessionResult {
    data object Success : ResetSessionResult()
    data class Failure(val coreFailure: CoreFailure) : ResetSessionResult()
}
