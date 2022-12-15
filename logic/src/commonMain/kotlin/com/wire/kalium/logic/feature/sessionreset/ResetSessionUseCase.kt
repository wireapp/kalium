package com.wire.kalium.logic.feature.sessionreset

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.ProteusClientProvider
import com.wire.kalium.logic.feature.message.SessionResetSender
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * If the Cryptobox session between two users is broken it can sometimes be repaired by calling this use case
 */
internal interface ResetSessionUseCase {
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
                messageRepository.markMessagesAsDecryptionResolved(
                    conversationId,
                    userId,
                    clientId
                )
            }.fold(
                { ResetSessionResult.Failure(it) },
                { ResetSessionResult.Success }
            )

        })
    }
}

sealed class ResetSessionResult {
    object Success : ResetSessionResult()
    data class Failure(val coreFailure: CoreFailure) : ResetSessionResult()
}
