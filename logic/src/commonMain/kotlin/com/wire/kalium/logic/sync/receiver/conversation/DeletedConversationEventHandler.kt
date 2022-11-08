package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.message.EphemeralConversationNotification
import com.wire.kalium.logic.feature.message.EphemeralNotificationsMgr
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.firstOrNull

interface DeletedConversationEventHandler {
    suspend fun handle(event: Event.Conversation.DeletedConversation)
}

internal class DeletedConversationEventHandlerImpl(
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
    private val ephemeralNotificationsManager: EphemeralNotificationsMgr
) : DeletedConversationEventHandler {
    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun handle(event: Event.Conversation.DeletedConversation) {
        val conversation = conversationRepository.getConversationById(event.conversationId)
        if (conversation != null) {
            conversationRepository.deleteConversation(event.conversationId)
                .onFailure { coreFailure ->
                    logger.e("Error deleting the contents of a conversation $coreFailure")
                }.onSuccess {
                    val senderUser = userRepository.observeUser(event.senderUserId).firstOrNull()
                    val dataNotification = EphemeralConversationNotification(event, conversation, senderUser)
                    ephemeralNotificationsManager.scheduleNotification(dataNotification)
                    logger.d("Deleted the conversation ${event.conversationId}")
                }
        } else {
            logger.d("Skipping conversation delete event already handled")
        }
    }
}
