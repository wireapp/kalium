package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.kaliumLogger

actual class MessageSendingSchedulerImpl : MessageSendingScheduler {
    override suspend fun scheduleSendingOfPersistedMessage(conversationID: ConversationId, messageUuid: String) {
        kaliumLogger.w(
            "Scheduling of messages is not supported on JVM. " +
                    "Message of Conversation=$conversationID and UUID=$messageUuid won't be scheduled for sending."
        )
    }
}
