package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.user.UserId

/**
 * Result of passing an [Event] through [MLSMessageUnpacker] or [ProteusMessageUnpacker].
 */
internal sealed interface MessageUnpackResult {

    /**
     * The [Event] was successfully processed by the unpacker, and didn't result in
     * any [ApplicationMessage], only protocol-specific signaling/handshake.
     */
    object HandshakeMessage : MessageUnpackResult

    /**
     * The processed [Event] was successfully processed and resulted in a [ApplicationMessage].
     * This message should be handled according to its content by the caller.
     */
    data class ApplicationMessage(
        val conversationId: ConversationId,
        val timestampIso: String,
        val senderUserId: UserId,
        val senderClientId: ClientId,
        val content: ProtoContent.Readable
    ) : MessageUnpackResult

}
