package com.wire.kalium.logic.feature.message

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class SessionResetSender internal constructor(
    private val selfUserId: QualifiedID,
    private val slowSyncRepository: SlowSyncRepository,
    private val messageSender: MessageSender,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {
    suspend operator fun invoke(
        conversationId: ConversationId,
        clientId: ClientId,
    ): Either<CoreFailure, Unit> = withContext(dispatchers.io) {
        slowSyncRepository.slowSyncStatus.first {
            it is SlowSyncStatus.Complete
        }
        val generatedMessageUuid = uuid4().toString()

        val message = Message.Signaling(
            id = generatedMessageUuid,
            content = MessageContent.SessionReset,
            conversationId = conversationId,
            date = Clock.System.now().toString(),
            senderUserId = selfUserId,
            senderClientId = clientId,
            status = Message.Status.SENT
        )
        val recipient = Recipient(selfUserId, listOf(clientId))
        messageSender.sendMessage(message, MessageTarget.Client(listOf(recipient)))
    }

}
