package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.message.ProtoContent.ExternalMessageInstructions
import com.wire.kalium.persistence.dao.ConversationEntity.ProtocolInfo.Proteus
import com.wire.kalium.protobuf.messages.GenericMessage

/**
 * The result of the [protobuf model](https://github.com/wireapp/generic-message-proto) parsing.
 *
 * It can be [ProtoContent] or [ExternalMessageInstructions].
 */
sealed class ProtoContent {

    /**
     * Regular message, with readable content that can be simply used.
     * @see [ExternalMessageInstructions]
     */
    data class Readable(
        val messageUid: String,
        val messageContent: MessageContent.FromProto
    ) : ProtoContent()

    /**
     * The message doesn't contain an actual content,
     * but rather instructions on how to read an external message.
     *
     * This message content is used if original message results in large payload,
     * that would not be accepted by backend. Regular messages over [Proteus] are encrypted multiple times (per recipient)
     * and in case of multiple participants even quite small message can generate huge payload.
     * In that case we want to encrypt original message with symmetric encryption and only send a key to all participants.
     *
     * See Also: [External Proto Message](https://github.com/wireapp/generic-message-proto#external))
     * @see [GenericMessage.Content.External]
     */
    class ExternalMessageInstructions(
        val messageUid: String,
        val otrKey: ByteArray,
        val sha256: ByteArray?,
        val encryptionAlgorithm: MessageEncryptionAlgorithm?
    ) : ProtoContent()
}
