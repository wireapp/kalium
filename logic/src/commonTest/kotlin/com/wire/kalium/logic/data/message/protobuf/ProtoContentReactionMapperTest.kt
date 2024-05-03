/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.message.protobuf

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.ProtoContentMapperImpl
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.protobuf.decodeFromByteArray
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.messages.GenericMessage
import com.wire.kalium.protobuf.messages.Reaction
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProtoContentReactionMapperTest {

    private lateinit var protoContentMapper: ProtoContentMapper
    val selfUserId = UserId("user-id", "domain")

    @BeforeTest
    fun setup() {
        protoContentMapper = ProtoContentMapperImpl(selfUserId = selfUserId)
    }

    @Test
    fun givenReactionMessage_whenDecodingProto_thenTheCorrectReferencedMessageIdShouldBeReturned() {
        val referencedMessageId = "my cool message ID"

        val encodedProtobuf = GenericMessage(
            TEST_MESSAGE_UUID,
            content = GenericMessage.Content.Reaction(Reaction(null, referencedMessageId))
        ).encodeToByteArray()

        val result = protoContentMapper.decodeFromProtobuf(PlainMessageBlob(encodedProtobuf))
        assertIs<ProtoContent.Readable>(result)
        val content = result.messageContent
        assertIs<MessageContent.Reaction>(content)

        assertEquals(referencedMessageId, content.messageId)
    }

    @Test
    fun givenNullEmojiString_whenDecodingProto_thenEmptyEmojiSetShouldBeReturned() {
        val encodedProtobuf = GenericMessage(
            TEST_MESSAGE_UUID,
            content = GenericMessage.Content.Reaction(Reaction(null, TEST_MESSAGE_UUID))
        ).encodeToByteArray()

        val result = protoContentMapper.decodeFromProtobuf(PlainMessageBlob(encodedProtobuf))
        assertIs<ProtoContent.Readable>(result)
        val content = result.messageContent
        assertIs<MessageContent.Reaction>(content)

        assertTrue(content.emojiSet.isEmpty())
    }

    @Test
    fun givenSingleBlankEmojiString_whenDecodingProto_thenTheResultShouldNotContainAnyReactions() {
        val emojiString = "            "
        val encodedProtobuf = GenericMessage(
            TEST_MESSAGE_UUID,
            content = GenericMessage.Content.Reaction(Reaction(emojiString, "OtherMessageId"))
        ).encodeToByteArray()

        val result = protoContentMapper.decodeFromProtobuf(PlainMessageBlob(encodedProtobuf))
        assertIs<ProtoContent.Readable>(result)
        val content = result.messageContent
        assertIs<MessageContent.Reaction>(content)

        assertTrue(content.emojiSet.isEmpty())
    }

    @Test
    fun givenMultipleEmojiSeparatedByCommas_whenDecodingProto_thenTheResultShouldContainAllEmoji() {
        val emojis = setOf("🤠", "🤌🏼", "🫡", "🫥")
        val emojiString = emojis.joinToString(separator = ",")

        val encodedProtobuf = GenericMessage(
            TEST_MESSAGE_UUID,
            content = GenericMessage.Content.Reaction(Reaction(emojiString, "OtherMessageId"))
        ).encodeToByteArray()

        val result = protoContentMapper.decodeFromProtobuf(PlainMessageBlob(encodedProtobuf))
        assertIs<ProtoContent.Readable>(result)
        val content = result.messageContent
        assertIs<MessageContent.Reaction>(content)

        assertContentEquals(emojis.sorted(), content.emojiSet.sorted())
    }

    @Test
    fun givenBlankReactionsSeparatedByCommas_whenDecodingProto_thenTheResultShouldNotContainBlankReactions() {
        val validReactions = setOf("🤠", "🤌🏼", "🫡", "🫥")
        val blankReactions = setOf(" ", Char(0x00a0), "")
        val emojiString = (validReactions + blankReactions).joinToString(separator = ",")

        emojiString.isBlank()
        val encodedProtobuf = GenericMessage(
            TEST_MESSAGE_UUID,
            content = GenericMessage.Content.Reaction(Reaction(emojiString, "OtherMessageId"))
        ).encodeToByteArray()

        val result = protoContentMapper.decodeFromProtobuf(PlainMessageBlob(encodedProtobuf))
        assertIs<ProtoContent.Readable>(result)
        val content = result.messageContent
        assertIs<MessageContent.Reaction>(content)

        assertContentEquals(validReactions.sorted(), content.emojiSet.sorted())
    }

    @Test
    fun givenReactionsWithBlankSpacesAroundTThemSeparatedByCommas_whenDecodingProto_thenTheResultShouldTrimReactions() {
        val whitespaceChar = Char(0x00a0)
        val inputReactions = setOf("🤠 ", " 🤌🏼", "  🫡  ", "$whitespaceChar🫥$whitespaceChar")
        val expectedReactions = setOf("🤠", "🤌🏼", "🫡", "🫥")
        val emojiString = inputReactions.joinToString(separator = ",")

        emojiString.isBlank()
        val encodedProtobuf = GenericMessage(
            TEST_MESSAGE_UUID,
            content = GenericMessage.Content.Reaction(Reaction(emojiString, "OtherMessageId"))
        ).encodeToByteArray()

        val result = protoContentMapper.decodeFromProtobuf(PlainMessageBlob(encodedProtobuf))
        assertIs<ProtoContent.Readable>(result)
        val content = result.messageContent
        assertIs<MessageContent.Reaction>(content)

        assertContentEquals(expectedReactions.sorted(), content.emojiSet.sorted())

        CompletableDeferred("SomeValue")
    }

    @Test
    fun givenSingleBlankEmoji_whenEncodingToProto_thenTheResultShouldNotContainAnyReactions() {
        val emojiSet = setOf("      ")
        val protoContent = ProtoContent.Readable(
            "Whatever",
            MessageContent.Reaction("referencedMessageId", emojiSet = emojiSet),
            false,
            Conversation.LegalHoldStatus.DISABLED
        )

        val result = protoContentMapper.encodeToProtobuf(protoContent)

        val encoded = GenericMessage.decodeFromByteArray(result.data)
        assertTrue(encoded.reaction!!.emoji!!.isEmpty())
    }

    @Test
    fun givenBlankEmojis_whenEncodingToProto_thenTheResultShouldNotContainAnyReactions() {
        val emojiSet = setOf("      ", "", Char(0x00a0).toString())
        val protoContent = ProtoContent.Readable(
            "Whatever",
            MessageContent.Reaction("referencedMessageId", emojiSet = emojiSet),
            false,
            Conversation.LegalHoldStatus.DISABLED
        )

        val result = protoContentMapper.encodeToProtobuf(protoContent)

        val encoded = GenericMessage.decodeFromByteArray(result.data)
        assertTrue(encoded.reaction!!.emoji!!.isEmpty())
    }

    @Test
    fun givenAReferencedMessageId_whenEncodingToProto_thenTheResultShouldContainTheSameMessageId() {
        val referencedMessageId = "referencedMessageID"
        val protoContent = ProtoContent.Readable(
            "messageId",
            MessageContent.Reaction(referencedMessageId, emojiSet = setOf()),
            false,
            Conversation.LegalHoldStatus.DISABLED
        )

        val result = protoContentMapper.encodeToProtobuf(protoContent)

        val encoded = GenericMessage.decodeFromByteArray(result.data)
        assertEquals(referencedMessageId, encoded.reaction!!.messageId)
    }

    private companion object {
        const val TEST_MESSAGE_UUID = "testUuid"
    }
}
