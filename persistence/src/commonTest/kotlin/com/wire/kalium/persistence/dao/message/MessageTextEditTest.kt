package com.wire.kalium.persistence.dao.message

import app.cash.turbine.test
import com.wire.kalium.persistence.dao.receipt.ReceiptTypeEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessageTextEditTest : BaseMessageTest() {

    @Test
    fun givenTextWasInserted_whenUpdatingContent_thenShouldUpdateTheId() = runTest {
        insertInitialData()

        messageDAO.updateTextMessageContent(
            editTimeStamp = "newDate",
            conversationId = CONVERSATION_ID,
            currentMessageId = ORIGINAL_MESSAGE_ID,
            newTextContent = ORIGINAL_CONTENT.copy(messageBody = "Howdy"),
            newMessageId = NEW_MESSAGE_ID
        )

        assertNull(messageDAO.getMessageById(ORIGINAL_MESSAGE_ID, CONVERSATION_ID).first())
        assertNotNull(messageDAO.getMessageById(NEW_MESSAGE_ID, CONVERSATION_ID).first())
    }

    @Test
    fun givenTextWasInsertedAndReacted_whenUpdatingMessageBody_thenContentShouldHaveNewMessageBody() = runTest {
        insertInitialData()
        reactionDAO.insertReaction(ORIGINAL_MESSAGE_ID, CONVERSATION_ID, SELF_USER_ID, "Date", "💁‍♂️")
        reactionDAO.insertReaction(ORIGINAL_MESSAGE_ID, CONVERSATION_ID, OTHER_USER_2.id, "Date", "🤌️")

        val newMessageBody = "newBody"

        messageDAO.updateTextMessageContent(
            editTimeStamp = "newDate",
            conversationId = CONVERSATION_ID,
            currentMessageId = ORIGINAL_MESSAGE_ID,
            newTextContent = ORIGINAL_CONTENT.copy(messageBody = newMessageBody),
            newMessageId = NEW_MESSAGE_ID
        )

        val result = messageDAO.getMessageById(NEW_MESSAGE_ID, CONVERSATION_ID).first()!!

        val content = result.content
        assertIs<MessageEntityContent.Text>(content)

        assertEquals(newMessageBody, content.messageBody)
    }

    @Test
    fun givenTextWasInsertedAndWithReceipts_whenUpdatingMessageBody_thenContentShouldHaveNewMessageBody() = runTest {
        insertInitialData()
        receiptDAO.insertReceipts(OTHER_USER.id, CONVERSATION_ID, Clock.System.now(), ReceiptTypeEntity.READ, listOf(ORIGINAL_MESSAGE_ID))

        val newMessageBody = "newBody"

        messageDAO.updateTextMessageContent(
            editTimeStamp = "newDate",
            conversationId = CONVERSATION_ID,
            currentMessageId = ORIGINAL_MESSAGE_ID,
            newTextContent = ORIGINAL_CONTENT.copy(messageBody = newMessageBody),
            newMessageId = NEW_MESSAGE_ID
        )

        val result = messageDAO.getMessageById(NEW_MESSAGE_ID, CONVERSATION_ID).first()!!

        val content = result.content
        assertIs<MessageEntityContent.Text>(content)

        assertEquals(newMessageBody, content.messageBody)
    }

    @Test
    fun givenTextWasInserted_whenUpdatingContentWithMentions_thenShouldAddMentions() = runTest {
        insertInitialData()

        val mentions = listOf(MessageEntity.Mention(0, 1, OTHER_USER_2.id))
        messageDAO.updateTextMessageContent(
            editTimeStamp = "newDate",
            conversationId = CONVERSATION_ID,
            currentMessageId = ORIGINAL_MESSAGE_ID,
            newTextContent = ORIGINAL_CONTENT.copy(
                messageBody = "Howdy",
                mentions = mentions
            ),
            newMessageId = NEW_MESSAGE_ID
        )

        val result = messageDAO.getMessageById(NEW_MESSAGE_ID, CONVERSATION_ID).first()!!

        val content = result.content
        assertIs<MessageEntityContent.Text>(content)

        assertContentEquals(mentions, content.mentions)
    }

    @Test
    fun givenTextWasInsertedAndReacted_whenUpdatingContent_thenShouldClearReactions() = runTest {
        insertInitialData()

        messageDAO.updateTextMessageContent(
            editTimeStamp = "newDate",
            conversationId = CONVERSATION_ID,
            currentMessageId = ORIGINAL_MESSAGE_ID,
            newTextContent = ORIGINAL_CONTENT.copy(messageBody = "Howdy"),
            newMessageId = NEW_MESSAGE_ID
        )

        reactionDAO.observeMessageReactions(CONVERSATION_ID, NEW_MESSAGE_ID).test {
            val reactions = awaitItem()
            assertTrue(reactions.isEmpty())
        }
    }

    @Test
    fun givenTextWasInsertedAndReceiptsAttached_whenUpdatingContent_thenReceiptsRemainAfterBeingEdited() = runTest {
        insertInitialData()
        val instant = Clock.System.now()
        receiptDAO.insertReceipts(OTHER_USER.id, CONVERSATION_ID, instant, ReceiptTypeEntity.READ, listOf(ORIGINAL_MESSAGE_ID))

        messageDAO.updateTextMessageContent(
            editTimeStamp = "newDate",
            conversationId = CONVERSATION_ID,
            currentMessageId = ORIGINAL_MESSAGE_ID,
            newTextContent = ORIGINAL_CONTENT.copy(messageBody = "Howdy"),
            newMessageId = NEW_MESSAGE_ID
        )

        receiptDAO.observeDetailedReceiptsForMessage(CONVERSATION_ID, NEW_MESSAGE_ID, ReceiptTypeEntity.READ).test {
            val receipts = awaitItem()
            assertEquals(1, receipts.size)

            assertEquals(OTHER_USER.id, receipts.first().userId)
            assertEquals(instant, receipts.first().date)
        }
    }

    @Test
    fun givenTextWasInserted_whenUpdatingContent_thenShouldMarkAsEditedWithNewDate() = runTest {
        insertInitialData()

        val editDate = "newDate"
        messageDAO.updateTextMessageContent(
            editTimeStamp = editDate,
            conversationId = CONVERSATION_ID,
            currentMessageId = ORIGINAL_MESSAGE_ID,
            newTextContent = ORIGINAL_CONTENT.copy(messageBody = "Howdy"),
            newMessageId = NEW_MESSAGE_ID
        )

        val result = messageDAO.getMessageById(NEW_MESSAGE_ID, CONVERSATION_ID).first()!!

        assertIs<MessageEntity.Regular>(result)
        val editStatus = result.editStatus
        assertIs<MessageEntity.EditStatus.Edited>(editStatus)
        assertEquals(editDate, editStatus.lastTimeStamp)
    }

    override suspend fun insertInitialData() {
        super.insertInitialData()
        messageDAO.insertOrIgnoreMessage(ORIGINAL_MESSAGE)
    }

    private companion object {
        const val ORIGINAL_MESSAGE_ID = "originalMessageId"
        const val NEW_MESSAGE_ID = "newMessageId"
        val ORIGINAL_SENDER_ID = OTHER_USER.id
        val CONVERSATION_ID = TEST_CONVERSATION_1.id
        val ORIGINAL_CONTENT = MessageEntityContent.Text(messageBody = "Hello")
        val ORIGINAL_MESSAGE = newRegularMessageEntity(
            id = ORIGINAL_MESSAGE_ID,
            content = ORIGINAL_CONTENT,
            conversationId = CONVERSATION_ID,
            senderUserId = ORIGINAL_SENDER_ID
        )
    }
}
