package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.user.UserId

internal sealed interface MessageUnpackResult {

    object ProtocolMessage : MessageUnpackResult

    data class ApplicationMessage(
        val conversationId: ConversationId,
        val timestampIso: String,
        val senderUserId: UserId,
        val senderClientId: ClientId,
        val content: ProtoContent.Readable
    ) : MessageUnpackResult

}
