package com.wire.kalium.logic.feature.sessionreset

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.ProteusClientProvider
import com.wire.kalium.logic.feature.message.SessionResetSender
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.sync.SyncManager

interface ResetSessionUseCase {
    suspend operator fun invoke(userId: UserId, clientId: ClientId): Either<CoreFailure, Unit>
}
// TODO unit test in next PR
class ResetSessionUseCaseImpl internal constructor(
    private val syncManager: SyncManager,
    private val proteusClientProvider: ProteusClientProvider,
    private val sessionResetSender: SessionResetSender,
    private val messageRepository: MessageRepository,
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : ResetSessionUseCase {
    override suspend operator fun invoke(userId: UserId, clientId: ClientId): Either<CoreFailure, Unit> {
        syncManager.waitUntilLive()
        return proteusClientProvider.getOrError().fold({
            return@fold Either.Left(it)
        }, {
            val conversationId = messageRepository.getConversationIdByUserIdAndClientId(userId, clientId)
            val cryptoUserID = idMapper.toCryptoQualifiedIDId(userId)
            val cryptoSessionId = CryptoSessionId(
                userId = cryptoUserID,
                cryptoClientId = CryptoClientId(clientId.value)
            )
            it.deleteSession(cryptoSessionId)
            // TODO("Update device verified state to false once implemented")
            return@fold sessionResetSender(
                conversationId = conversationId,
                userId = userId,
                clientId = clientId
            )
        })
    }
}
