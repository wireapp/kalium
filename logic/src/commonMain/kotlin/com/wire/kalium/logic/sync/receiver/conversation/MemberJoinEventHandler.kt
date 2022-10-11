package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger

interface MemberJoinEventHandler {
    suspend fun handle(event: Event.Conversation.MemberJoin): Either<CoreFailure, Unit>
}

internal class MemberJoinEventHandlerImpl(
    private val conversationRepository: ConversationRepository,
    private val persistMessage: PersistMessageUseCase
) : MemberJoinEventHandler {
    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun handle(event: Event.Conversation.MemberJoin) =
        // Attempt to fetch conversation details if needed, as this might be an unknown conversation
        conversationRepository.fetchConversationIfUnknown(event.conversationId)
            .run {
                onSuccess {
                    logger.v("Succeeded fetching conversation details on MemberJoin Event: $event")
                }
                onFailure {
                    logger.w("Failure fetching conversation details on MemberJoin Event: $event")
                }
                // Even if unable to fetch conversation details, at least attempt adding the member
                conversationRepository.persistMembers(event.members, event.conversationId)
            }.onSuccess {
                val message = Message.System(
                    id = event.id,
                    content = MessageContent.MemberChange.Added(members = event.members.map { it.id }),
                    conversationId = event.conversationId,
                    date = event.timestampIso,
                    senderUserId = event.addedBy,
                    status = Message.Status.SENT,
                    visibility = Message.Visibility.VISIBLE
                )
                persistMessage(message)

            }.onFailure { logger.e("failure on member join event: $it") }
}
