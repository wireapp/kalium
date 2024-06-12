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

package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.utils.IgnoreIOS
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@IgnoreIOS
class MessageReplyTest : BaseMessageTest() {

    @Test
    fun givenInsertedTextMessage_whenNewMessageQuotesIt_thenNewMessageShouldBeQueriedWithQuoteInfo() = runTest(dispatcher) {
        insertInitialData()
        messageDAO.insertOrIgnoreMessage(MESSAGE_QUOTING_TEXT)

        val message = messageDAO.getMessageById(MESSAGE_QUOTING_TEXT.id, MESSAGE_QUOTING_TEXT.conversationId)

        assertNotNull(message)
        val content = message.content
        assertNotNull(content)
        assertIs<MessageEntityContent.Text>(content)

        assertEquals(ORIGINAL_TEXT_MESSAGE.id, content.quotedMessageId)
        val quotedMessage = content.quotedMessage
        assertNotNull(quotedMessage)
        assertEquals(ORIGINAL_MESSAGE_SENDER.id, quotedMessage.senderId)
        assertEquals(ORIGINAL_MESSAGE_SENDER.name, quotedMessage.senderName)
        assertEquals(ORIGINAL_TEXT_MESSAGE.date.toIsoDateTimeString(), quotedMessage.dateTime)
        assertEquals(ORIGINAL_TEXT_MESSAGE_CONTENT, quotedMessage.textBody)
        assertNull(quotedMessage.assetMimeType)
    }

    @Test
    fun givenInsertedAssetMessage_whenNewMessageQuotesIt_thenNewMessageShouldBeQueriedWithQuoteAndAssetInfo() = runTest(dispatcher) {
        insertInitialData()
        messageDAO.insertOrIgnoreMessage(MESSAGE_QUOTING_IMAGE)

        val message = messageDAO.getMessageById(MESSAGE_QUOTING_IMAGE.id, MESSAGE_QUOTING_IMAGE.conversationId)

        assertNotNull(message)
        val content = message.content
        assertNotNull(content)
        assertIs<MessageEntityContent.Text>(content)

        assertEquals(ORIGINAL_IMAGE_MESSAGE.id, content.quotedMessageId)
        val quotedMessage = content.quotedMessage
        assertNotNull(quotedMessage)
        assertEquals(ORIGINAL_MESSAGE_SENDER.id, quotedMessage.senderId)
        assertEquals(ORIGINAL_MESSAGE_SENDER.name, quotedMessage.senderName)
        assertEquals(ORIGINAL_IMAGE_MESSAGE.date.toIsoDateTimeString(), quotedMessage.dateTime)
        assertEquals(ORIGINAL_IMAGE_MESSAGE_ASSET_MIMETYPE, quotedMessage.assetMimeType)
        assertEquals(ORIGINAL_IMAGE_MESSAGE_NAME, quotedMessage.assetName)
        assertNull(quotedMessage.textBody)
    }

    @Test
    fun givenInsertedLocationMessage_whenNewMessageQuotesIt_thenNewMessageShouldBeQueriedWithQuoteAndLocationInfo() = runTest(dispatcher) {
        insertInitialData()
        messageDAO.insertOrIgnoreMessage(MESSAGE_QUOTING_LOCATION)

        val message = messageDAO.getMessageById(MESSAGE_QUOTING_LOCATION.id, MESSAGE_QUOTING_LOCATION.conversationId)

        assertNotNull(message)
        val content = message.content
        assertNotNull(content)
        assertIs<MessageEntityContent.Text>(content)

        assertEquals(ORIGINAL_LOCATION_MESSAGE.id, content.quotedMessageId)
        val quotedMessage = content.quotedMessage
        assertNotNull(quotedMessage)
        assertEquals(ORIGINAL_MESSAGE_SENDER.id, quotedMessage.senderId)
        assertEquals(ORIGINAL_MESSAGE_SENDER.name, quotedMessage.senderName)
        assertEquals(ORIGINAL_LOCATION_MESSAGE.date.toIsoDateTimeString(), quotedMessage.dateTime)
        assertEquals(ORIGINAL_LOCATION_NAME, quotedMessage.locationName)
        assertNull(quotedMessage.textBody)
    }

    override suspend fun insertInitialData() {
        super.insertInitialData()
        // Always insert original messages
        messageDAO.insertOrIgnoreMessage(ORIGINAL_TEXT_MESSAGE)
        messageDAO.insertOrIgnoreMessage(ORIGINAL_IMAGE_MESSAGE)
        messageDAO.insertOrIgnoreMessage(ORIGINAL_LOCATION_MESSAGE)
    }

    private companion object {
        val ORIGINAL_MESSAGE_SENDER = OTHER_USER
        const val ORIGINAL_TEXT_MESSAGE_CONTENT = "Something to think about"
        val ORIGINAL_TEXT_MESSAGE = newRegularMessageEntity(
            id = "originalMessage",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = ORIGINAL_MESSAGE_SENDER.id,
            content = MessageEntityContent.Text(ORIGINAL_TEXT_MESSAGE_CONTENT)
        )
        val MESSAGE_QUOTING_TEXT = newRegularMessageEntity(
            id = "quotingMessage",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = SELF_USER_ID,
            content = MessageEntityContent.Text(
                "I'm quoting you",
                quotedMessageId = ORIGINAL_TEXT_MESSAGE.id
            )
        )
        const val ORIGINAL_IMAGE_MESSAGE_ASSET_MIMETYPE = "jpeg"
        const val ORIGINAL_IMAGE_MESSAGE_NAME = "someAssetName"
        val ORIGINAL_IMAGE_MESSAGE = newRegularMessageEntity(
            id = "originalAssetMessage",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = ORIGINAL_MESSAGE_SENDER.id,
            content = MessageEntityContent.Asset(
                assetSizeInBytes = 42L,
                assetMimeType = ORIGINAL_IMAGE_MESSAGE_ASSET_MIMETYPE,
                assetOtrKey = byteArrayOf(),
                assetSha256Key = byteArrayOf(),
                assetName = ORIGINAL_IMAGE_MESSAGE_NAME,
                assetId = "someId",
                assetDomain = "someDomain",
                assetEncryptionAlgorithm = null
            )
        )
        val MESSAGE_QUOTING_IMAGE = newRegularMessageEntity(
            id = "quotingMessage",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = SELF_USER_ID,
            content = MessageEntityContent.Text(
                "I'm quoting you",
                quotedMessageId = ORIGINAL_IMAGE_MESSAGE.id
            )
        )

        const val ORIGINAL_LOCATION_NAME = "someSecretLocation"
        val ORIGINAL_LOCATION_MESSAGE = newRegularMessageEntity(
            id = "originalLocationMessage",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = ORIGINAL_MESSAGE_SENDER.id,
            content = MessageEntityContent.Location(
                latitude = 42.0f,
                longitude = -42.0f,
                name = ORIGINAL_LOCATION_NAME,
                zoom = 20
            )
        )

        val MESSAGE_QUOTING_LOCATION = newRegularMessageEntity(
            id = "quotingMessage",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = SELF_USER_ID,
            content = MessageEntityContent.Text(
                "I'm quoting your location",
                quotedMessageId = ORIGINAL_LOCATION_MESSAGE.id
            )
        )
    }
}
