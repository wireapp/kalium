package com.wire.kalium.persistence.dao.message

import app.cash.turbine.test
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newSystemMessageEntity
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MessageUnreadCounterTest : BaseMessageTest() {

    @Test
    fun givenUnreadRegularMessagesFromOthers_thenShouldCount() = runTest {
        insertInitialData()
        val readDate = Instant.fromEpochSeconds(10)
        val convId = TEST_CONVERSATION_1.id
        conversationDAO.updateConversationReadDate(convId, readDate.toIsoDateTimeString())

        val unreadMessages = listOf(
            newRegularMessageEntity(
                id = "1",
                conversationId = convId,
                senderUserId = OTHER_USER.id,
                content = MessageEntityContent.Text("Hi"),
                date = readDate + 1.seconds
            ),
            newRegularMessageEntity(
                id = "2",
                conversationId = convId,
                senderUserId = OTHER_USER.id,
                content = MessageEntityContent.Knock(true),
                date = readDate + 1.minutes
            )
        )
        messageDAO.insertOrIgnoreMessages(unreadMessages)

        val result = messageDAO.observeUnreadMessageCounter().first()[convId]
        assertNotNull(result)
        assertEquals(unreadMessages.size, result)
    }

    @Test
    fun givenOnlyReadTextMessages_thenShouldEmitEmptyMap() = runTest {
        insertInitialData()
        val readDate = Instant.fromEpochSeconds(10)
        val convId = TEST_CONVERSATION_1.id
        conversationDAO.updateConversationReadDate(convId, readDate.toIsoDateTimeString())

        val unreadMessages = listOf(
            newRegularMessageEntity(
                id = "1",
                conversationId = convId,
                senderUserId = OTHER_USER.id,
                content = MessageEntityContent.Text("Hi"),
                date = readDate - 1.seconds
            ),
            newRegularMessageEntity(
                id = "2",
                conversationId = convId,
                senderUserId = OTHER_USER.id,
                content = MessageEntityContent.Knock(true),
                date = readDate - 1.minutes
            )
        )
        messageDAO.insertOrIgnoreMessages(unreadMessages)

        val result = messageDAO.observeUnreadMessageCounter().first()[convId]
        assertNull(result)
    }

    @Test
    fun givenUnreadSystemMessages_thenShouldNotCount() = runTest {
        insertInitialData()
        val readDate = Instant.fromEpochSeconds(10)
        val convId = TEST_CONVERSATION_1.id
        conversationDAO.updateConversationReadDate(convId, readDate.toIsoDateTimeString())

        val unreadMessages = listOf(
            newSystemMessageEntity(
                id = "1",
                conversationId = convId,
                senderUserId = OTHER_USER.id,
                content = MessageEntityContent.ConversationRenamed("Hehe"),
                date = readDate + 1.seconds
            ),
            newSystemMessageEntity(
                id = "2",
                conversationId = convId,
                senderUserId = OTHER_USER.id,
                content = MessageEntityContent.TeamMemberRemoved("Hehe"),
                date = readDate + 1.minutes
            )
        )
        messageDAO.insertOrIgnoreMessages(unreadMessages)

        val result = messageDAO.observeUnreadMessageCounter().first()[convId]
        assertNull(result)
    }

    @Test
    fun givenNewUnreadMessageIsInserted_thenShouldEmitUpdate() = runTest {
        insertInitialData()
        val readDate = Instant.fromEpochSeconds(10)
        val convId = TEST_CONVERSATION_1.id
        conversationDAO.updateConversationReadDate(convId, readDate.toIsoDateTimeString())

        val unreadMessages = listOf(
            newRegularMessageEntity(
                id = "1",
                conversationId = convId,
                senderUserId = OTHER_USER.id,
                content = MessageEntityContent.Text("Hi"),
                date = readDate + 1.seconds
            )
        )
        messageDAO.insertOrIgnoreMessages(unreadMessages)

        messageDAO.observeUnreadMessageCounter().test {

            awaitItem()[convId].also {
                assertNotNull(it)
                assertEquals(unreadMessages.size, it)
            }

            messageDAO.insertOrIgnoreMessage(
                newRegularMessageEntity(
                    id = "2",
                    conversationId = convId,
                    senderUserId = OTHER_USER.id,
                    content = MessageEntityContent.Knock(true),
                    date = readDate + 1.minutes
                )
            )

            awaitItem()[convId].also {
                assertNotNull(it)
                assertEquals(unreadMessages.size + 1, it)
            }
        }
    }
}
