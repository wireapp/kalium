package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.utils.IgnoreIOS
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import kotlinx.coroutines.flow.first
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
        messageDAO.insertMessage(MESSAGE_QUOTING_TEXT)

        val message = messageDAO.getMessageById(MESSAGE_QUOTING_TEXT.id, MESSAGE_QUOTING_TEXT.conversationId).first()

        assertNotNull(message)
        val content = message.content
        assertNotNull(content)
        assertIs<MessageEntityContent.Text>(content)

        assertEquals(ORIGINAL_TEXT_MESSAGE.id, content.quotedMessageId)
        val quotedMessage = content.quotedMessage
        assertNotNull(quotedMessage)
        assertEquals(ORIGINAL_MESSAGE_SENDER.id, quotedMessage.senderId)
        assertEquals(ORIGINAL_MESSAGE_SENDER.name, quotedMessage.senderName)
        assertEquals(ORIGINAL_TEXT_MESSAGE.date, quotedMessage.dateTime)
        assertEquals(ORIGINAL_TEXT_MESSAGE_CONTENT, quotedMessage.textBody)
        assertNull(quotedMessage.assetMimeType)
    }

    @Test
    fun givenInsertedAssetMessage_whenNewMessageQuotesIt_thenNewMessageShouldBeQueriedWithQuoteAndAssetInfo() = runTest(dispatcher) {
        insertInitialData()
        messageDAO.insertMessage(MESSAGE_QUOTING_IMAGE)

        val message = messageDAO.getMessageById(MESSAGE_QUOTING_IMAGE.id, MESSAGE_QUOTING_IMAGE.conversationId).first()

        assertNotNull(message)
        val content = message.content
        assertNotNull(content)
        assertIs<MessageEntityContent.Text>(content)

        assertEquals(ORIGINAL_IMAGE_MESSAGE.id, content.quotedMessageId)
        val quotedMessage = content.quotedMessage
        assertNotNull(quotedMessage)
        assertEquals(ORIGINAL_MESSAGE_SENDER.id, quotedMessage.senderId)
        assertEquals(ORIGINAL_MESSAGE_SENDER.name, quotedMessage.senderName)
        assertEquals(ORIGINAL_IMAGE_MESSAGE.date, quotedMessage.dateTime)
        assertEquals(ORIGINAL_IMAGE_MESSAGE_ASSET_MIMETYPE, quotedMessage.assetMimeType)
        assertEquals(ORIGINAL_IMAGE_MESSAGE_ASSET_ID, quotedMessage.assetId)
        assertEquals(ORIGINAL_IMAGE_MESSAGE_ASSET_DOMAIN, quotedMessage.assetDomain)
        assertNull(quotedMessage.textBody)
    }

    override suspend fun insertInitialData() {
        super.insertInitialData()
        // Always insert original messages
        messageDAO.insertMessage(ORIGINAL_TEXT_MESSAGE)
        messageDAO.insertMessage(ORIGINAL_IMAGE_MESSAGE)
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
        const val ORIGINAL_IMAGE_MESSAGE_ASSET_ID = "someAssetID"
        const val ORIGINAL_IMAGE_MESSAGE_ASSET_DOMAIN = "someAssetDomain"
        val ORIGINAL_IMAGE_MESSAGE = newRegularMessageEntity(
            id = "originalAssetMessage",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = ORIGINAL_MESSAGE_SENDER.id,
            content = MessageEntityContent.Asset(
                assetSizeInBytes = 42L,
                assetMimeType = ORIGINAL_IMAGE_MESSAGE_ASSET_MIMETYPE,
                assetOtrKey = byteArrayOf(),
                assetSha256Key = byteArrayOf(),
                assetId = ORIGINAL_IMAGE_MESSAGE_ASSET_ID,
                assetDomain = ORIGINAL_IMAGE_MESSAGE_ASSET_DOMAIN,
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
    }
}
