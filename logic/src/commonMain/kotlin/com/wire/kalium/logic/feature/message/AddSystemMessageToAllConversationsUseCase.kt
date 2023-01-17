package com.wire.kalium.logic.feature.message

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.kaliumLogger

import com.wire.kalium.util.DateTimeUtil

/**
 * persist a local system message to all conversations
 */
interface AddSystemMessageToAllConversationsUseCase {
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}

class AddSystemMessageToAllConversationsUseCaseImpl internal constructor(
    private val messageRepository: MessageRepository,
    private val slowSyncRepository: SlowSyncRepository,
    private val selfUserId: UserId
) : AddSystemMessageToAllConversationsUseCase {
    override suspend operator fun invoke(): Either<CoreFailure, Unit> {
        if (!slowSyncRepository.needsToPersistHistoryLostMessage()) return Either.Right(Unit)

        kaliumLogger.w("persist HistoryLost system message after recovery for all conversations")
        val generatedMessageUuid = uuid4().toString()
        val message = Message.System(
            id = generatedMessageUuid,
            content = MessageContent.HistoryLost,
            // the conversation id will be ignored in the repo level!
            conversationId = ConversationId("", ""),
            date = DateTimeUtil.currentIsoDateTimeString(),
            senderUserId = selfUserId,
            status = Message.Status.SENT,
        )
        return messageRepository.persistSystemMessageToAllConversations(message)
    }
}
