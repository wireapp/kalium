/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.CryptoQualifiedID
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.DecryptedMessageBundle
import com.wire.kalium.logic.data.conversation.toModel
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapperImpl
import com.wire.kalium.logic.data.user.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import com.wire.kalium.cryptography.DecryptedMessageBundle as CryptoDecryptedMessageBundle

class MLSDeliveryFlagsTest {
    private val self = UserId("self", "example.com")
    private val protoContentMapper = ProtoContentMapperImpl(selfUserId = self)

    @Test
    fun givenEachMLSMessageKind_whenUnpackingDecryptedBundle_thenDeliveryFlagsAreKept() {
        listOf(false to false, true to false, false to true, true to true).forEach { (targeted, transient) ->
            val message = assertIs<MessageUnpackResult.ApplicationMessage>(
                decryptedBundle(MessageContent.Text("text"), targeted, transient)
                    .toModel(GROUP_ID)
                    .unpack(CONVERSATION_ID, INSTANT, protoContentMapper)
            )

            assertEquals(targeted, message.targeted)
            assertEquals(transient, message.transient)
        }
    }

    @Test
    fun givenFlaggedApplicationMessage_whenConvertingToMessage_thenRegularAndSignalingKeepDeliveryFlags() {
        val regular = applicationMessage(MessageContent.Text("text"), targeted = true, transient = false).toRegularMessage(self)
        val signaling = applicationMessage(MessageContent.Calling("call"), targeted = false, transient = true).toSignalingMessage(self)

        assertTrue(regular.targeted)
        assertFalse(regular.transient)
        assertFalse(signaling.targeted)
        assertTrue(signaling.transient)
    }

    @Test
    fun givenDeliveryFlags_whenCheckingPolicy_thenOnlyApplicationMessagesAreAccepted() {
        assertTrue(applicationMessage(MessageContent.Text("text"), targeted = false, transient = false).isAcceptedDelivery())
        assertFalse(applicationMessage(MessageContent.Text("text"), targeted = true, transient = false).isAcceptedDelivery())
        assertFalse(applicationMessage(MessageContent.Text("text"), targeted = false, transient = true).isAcceptedDelivery())
        assertFalse(applicationMessage(MessageContent.Calling("call"), targeted = true, transient = true).isAcceptedDelivery())
        assertFalse(
            applicationMessage(MessageContent.Calling("call"), targeted = true, transient = false).toSignalingMessage(self)
                .isAcceptedDelivery()
        )
    }

    private fun decryptedBundle(content: MessageContent.FromProto, targeted: Boolean, transient: Boolean) =
        CryptoDecryptedMessageBundle.Text(
            message = protoContentMapper.encodeToProtobuf(protoContent(content)).data,
            senderClientId = CryptoQualifiedClientId("client", CryptoQualifiedID("sender", "example.com")),
            identity = null,
            targeted = targeted,
            transient = transient
        )

    private fun applicationMessage(content: MessageContent.FromProto, targeted: Boolean, transient: Boolean) =
        MessageUnpackResult.ApplicationMessage(
            conversationId = CONVERSATION_ID,
            instant = INSTANT,
            senderUserId = UserId("sender", "example.com"),
            senderClientId = ClientId("client"),
            content = protoContent(content),
            targeted = targeted,
            transient = transient
        )

    private fun protoContent(content: MessageContent.FromProto) = ProtoContent.Readable(
        messageUid = "message",
        messageContent = content,
        expectsReadConfirmation = false,
        legalHoldStatus = Conversation.LegalHoldStatus.UNKNOWN,
    )

    private companion object {
        val GROUP_ID = GroupID("group")
        val CONVERSATION_ID = ConversationId("conversation", "example.com")
        val INSTANT = Instant.parse("2026-09-07T10:00:00Z")
    }
}
