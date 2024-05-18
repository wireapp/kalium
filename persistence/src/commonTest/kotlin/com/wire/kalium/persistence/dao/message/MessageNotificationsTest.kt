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

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class MessageNotificationsTest : BaseMessageTest() {

    @Test
    fun givenConversationLastNotifiedDateIsNull_whenNewMessageInserted_thenNotificationPropagated() = runTest {
        val message = OTHER_MESSAGE
        insertInitialData()

        messageDAO.insertOrIgnoreMessage(message)

        assertEquals(1, messageDAO.getNotificationMessage().size)
    }

    @Test
    fun givenConversationWithMessages_whenConversationLastNotifiedDateUpdated_thenNotificationListEmpty() = runTest {
        val message = OTHER_MESSAGE
        insertInitialData()
        messageDAO.insertOrIgnoreMessage(message)

        conversationDAO.updateConversationNotificationDate(TEST_CONVERSATION_1.id)

        assertEquals(0, messageDAO.getNotificationMessage().size)
    }

    @Test
    fun givenConversationWithMessages_whenConversationModifiedDateUpdated_thenNotificationNotAffected() = runTest {
        val message = OTHER_MESSAGE
        val date = message.date.plus(2.0.hours)
        insertInitialData()
        messageDAO.insertOrIgnoreMessage(message)

        messageDAO.getNotificationMessage().let {
            assertEquals(1, it.size)
            conversationDAO.updateConversationModifiedDate(TEST_CONVERSATION_1.id, date)
        }
    }

    @Test
    fun givenMutedConversation_whenNewMessageInserted_thenNotificationEmpty() = runTest {
        val message = OTHER_MESSAGE
        insertInitialData()
        conversationDAO.updateConversationMutedStatus(TEST_CONVERSATION_1.id, ConversationEntity.MutedStatus.ALL_MUTED, 0L)

        messageDAO.insertOrIgnoreMessage(message)

        assertEquals(0, messageDAO.getNotificationMessage().size)
    }

    @Test
    fun givenConversation_whenMessageWithReplyOnMyMessageInserted_thenNotificationMarkedAsReply() = runTest {
        val message = SELF_MESSAGE
        val replyMessage = OTHER_QUOTING_SELF
        insertInitialData()
        messageDAO.insertOrIgnoreMessages(listOf(message, replyMessage))

        messageDAO.getNotificationMessage().let { notifications ->
            assertEquals(1, notifications.size)
            assertEquals(true, notifications[0].isQuotingSelf)
        }
    }

    @Test
    fun givenConversation_whenMessageWithReplyOnOtherMessageInserted_thenNotificationIsNotMarkedAsReply() = runTest {
        val message = OTHER_MESSAGE
        val replyMessage = OTHER_QUOTING_OTHERS
        insertInitialData()
        messageDAO.insertOrIgnoreMessages(listOf(message, replyMessage))

        messageDAO.getNotificationMessage().let { notifications ->
            assertEquals(2, notifications.size)
            assertEquals(false, notifications.any { it.isQuotingSelf })
        }
    }

    @Test
    fun givenNewMessageInserted_whenConvInAllMutedState_thenNeedsToBeNotifyIsFalse() = runTest {
        val conversationMutedStatus = ConversationEntity.MutedStatus.ALL_MUTED
        val userStatus = UserAvailabilityStatusEntity.BUSY // Doesn't matter in this case
        val messageNeedsToBeNotified = false
        val message = OTHER_MESSAGE
        doTheTest(conversationMutedStatus, userStatus, messageNeedsToBeNotified, message)
    }

    @Test
    fun givenSelfMessageInserted_whenConvInAllMutedState_thenNeedsToBeNotifyIsFalse() = runTest {
        val conversationMutedStatus = ConversationEntity.MutedStatus.ALL_MUTED
        val userStatus = UserAvailabilityStatusEntity.BUSY // Doesn't matter in this case
        val messageNeedsToBeNotified = false
        val message = SELF_MESSAGE
        doTheTest(conversationMutedStatus, userStatus, messageNeedsToBeNotified, message)
    }

    @Test
    fun givenSelfMessageInserted_whenConvInAllAllowedState_AndUserInBusyMode_thenNeedsToBeNotifyIsFalse() = runTest {
        val conversationMutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED
        val userStatus = UserAvailabilityStatusEntity.BUSY
        val messageNeedsToBeNotified = false
        val message = SELF_MESSAGE
        doTheTest(conversationMutedStatus, userStatus, messageNeedsToBeNotified, message)
    }

    @Test
    fun givenOtherMentionsOthersMessageInserted_whenConvInAllAllowedState_AndUserInBusyMode_thenNeedsToBeNotifyIsFalse() =
        runTest {
            val conversationMutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED
            val userStatus = UserAvailabilityStatusEntity.BUSY
            val messageNeedsToBeNotified = false
            val message = OTHER_MENTIONING_OTHERS
            doTheTest(conversationMutedStatus, userStatus, messageNeedsToBeNotified, message)
        }

    @Test
    fun givenOtherMentioningSelfMessageInserted_whenConvInAllAllowedState_AndUserInBusyMode_thenNeedsToBeNotifyIsTrue() =
        runTest {
            val conversationMutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED
            val userStatus = UserAvailabilityStatusEntity.BUSY
            val messageNeedsToBeNotified = true
            val message = OTHER_MENTIONING_SELF
            doTheTest(conversationMutedStatus, userStatus, messageNeedsToBeNotified, message)
        }

    @Test
    fun givenOtherQuotingOthersMessageInserted_whenConvInAllAllowedState_AndUserInBusyMode_thenNeedsToBeNotifyIsFalse() =
        runTest {
            val conversationMutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED
            val userStatus = UserAvailabilityStatusEntity.BUSY
            val messageNeedsToBeNotified = false
            val message = OTHER_QUOTING_OTHERS
            doTheTest(conversationMutedStatus, userStatus, messageNeedsToBeNotified, message)
        }

    @Test
    fun givenOtherQuotingSelfMessageInserted_whenConvInAllAllowedState_AndUserInBusyMode_thenNeedsToBeNotifyIsTrue() =
        runTest {
            val conversationMutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED
            val userStatus = UserAvailabilityStatusEntity.BUSY
            val messageNeedsToBeNotified = true
            val message = OTHER_QUOTING_SELF
            doTheTest(conversationMutedStatus, userStatus, messageNeedsToBeNotified, message, SELF_MESSAGE)
        }

    @Test
    fun givenNewMessageInserted_whenConvInAllAllowedState_AndUserInAwayMode_thenNeedsToBeNotifyIsFalse() = runTest {
        val conversationMutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED
        val userStatus = UserAvailabilityStatusEntity.AWAY
        val messageNeedsToBeNotified = false
        val message = OTHER_MESSAGE
        doTheTest(conversationMutedStatus, userStatus, messageNeedsToBeNotified, message)
    }

    @Test
    fun givenSelfMessageInserted_whenConvInAllAllowedState_AndUserInNoneMode_thenNeedsToBeNotifyIsFalse() = runTest {
        val conversationMutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED
        val userStatus = UserAvailabilityStatusEntity.NONE
        val messageNeedsToBeNotified = false
        val message = SELF_MESSAGE
        doTheTest(conversationMutedStatus, userStatus, messageNeedsToBeNotified, message)
    }

    @Test
    fun givenNewMessageInserted_whenConvInAllAllowedState_AndUserInNoneMode_thenNeedsToBeNotifyIsTrue() = runTest {
        val conversationMutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED
        val userStatus = UserAvailabilityStatusEntity.NONE
        val messageNeedsToBeNotified = true

        insertInitialData()
        setConversationMutedStatus(OTHER_MESSAGE.conversationId, conversationMutedStatus)
        setUserAvailability(userStatus)
        messageDAO.insertOrIgnoreMessage(OTHER_MESSAGE)
        val result = messageDAO.needsToBeNotified(OTHER_MESSAGE.id, OTHER_MESSAGE.conversationId)
        assertEquals(messageNeedsToBeNotified, result)
    }

    @Test
    fun givenSelfMessageInserted_whenConvInAllAllowedState_AndUserInAvailableMode_thenNeedsToBeNotifyIsFalse() = runTest {
        val conversationMutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED
        val userStatus = UserAvailabilityStatusEntity.AVAILABLE
        val messageNeedsToBeNotified = false

        insertInitialData()
        setConversationMutedStatus(SELF_MESSAGE.conversationId, conversationMutedStatus)
        setUserAvailability(userStatus)
        messageDAO.insertOrIgnoreMessage(SELF_MESSAGE)
        val result = messageDAO.needsToBeNotified(SELF_MESSAGE.id, SELF_MESSAGE.conversationId)
        assertEquals(messageNeedsToBeNotified, result)
    }

    @Test
    fun givenNewMessageInserted_whenConvInAllAllowedState_AndUserInAvailableMode_thenNeedsToBeNotifyIsTrue() = runTest {
        val conversationMutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED
        val userStatus = UserAvailabilityStatusEntity.AVAILABLE
        val messageNeedsToBeNotified = true
        val message = OTHER_MESSAGE
        doTheTest(conversationMutedStatus, userStatus, messageNeedsToBeNotified, message)
    }

    @Test
    fun givenNewMessageInserted_whenConvInOnlyMentionsAndRepliesState_AndUserInAwayMode_thenNeedsToBeNotifyIsFalse() =
        runTest {
            val conversationMutedStatus = ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED
            val userStatus = UserAvailabilityStatusEntity.AWAY
            val messageNeedsToBeNotified = false
            val message = OTHER_MESSAGE
            doTheTest(conversationMutedStatus, userStatus, messageNeedsToBeNotified, message)
        }

    @Test
    fun givenSelfMessageInserted_whenConvInOnlyMentionsAndRepliesState_thenNeedsToBeNotifyIsFalse() = runTest {
        val conversationMutedStatus = ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED
        val userStatus = UserAvailabilityStatusEntity.AVAILABLE // must be other than away!
        val messageNeedsToBeNotified = false
        val message = SELF_MESSAGE
        doTheTest(conversationMutedStatus, userStatus, messageNeedsToBeNotified, message)
    }

    @Test
    fun givenOtherMentioningOthersMessageInserted_whenConvInOnlyMentionsAndRepliesState_thenNeedsToBeNotifyIsFalse() =
        runTest {
            val conversationMutedStatus = ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED
            val userStatus = UserAvailabilityStatusEntity.AVAILABLE // must be other than away!
            val messageNeedsToBeNotified = false
            val message = OTHER_MENTIONING_OTHERS
            doTheTest(conversationMutedStatus, userStatus, messageNeedsToBeNotified, message)
        }

    @Test
    fun givenOtherMentioningSelfMessageInserted_whenConvInOnlyMentionsAndRepliesState_thenNeedsToBeNotifyIsTrue() =
        runTest {
            val conversationMutedStatus = ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED
            val userStatus = UserAvailabilityStatusEntity.AVAILABLE // must be other than away!
            val messageNeedsToBeNotified = true
            val message = OTHER_MENTIONING_SELF
            doTheTest(conversationMutedStatus, userStatus, messageNeedsToBeNotified, message)
        }

    @Test
    fun givenOtherQuotingOthersMessageInserted_whenConvInOnlyMentionsAndRepliesState_thenNeedsToBeNotifyIsFalse() =
        runTest {
            val conversationMutedStatus = ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED
            val userStatus = UserAvailabilityStatusEntity.BUSY
            val messageNeedsToBeNotified = false
            val message = OTHER_QUOTING_OTHERS
            doTheTest(conversationMutedStatus, userStatus, messageNeedsToBeNotified, message)
        }

    @Test
    fun givenOtherQuotingSelfMessageInserted_whenConvInOnlyMentionsAndRepliesState_thenNeedsToBeNotifyIsTrue() = runTest {
        val conversationMutedStatus = ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED
        val userStatus = UserAvailabilityStatusEntity.BUSY
        val messageNeedsToBeNotified = true
        val message = OTHER_QUOTING_SELF
        doTheTest(conversationMutedStatus, userStatus, messageNeedsToBeNotified, message, SELF_MESSAGE)
    }

    @Test
    fun givenConversation_whenMessageSelfDeleteMessageInserted_thenIsSelfDeleteFlagSetToTrue() = runTest {
        val message = OTHER_MESSAGE.copy(expireAfterMs = 10000)
        insertInitialData()
        messageDAO.insertOrIgnoreMessages(listOf(message))

        messageDAO.getNotificationMessage().let { notifications ->
            assertTrue { notifications.first().isSelfDelete }
        }
    }

    @Test
    fun givenConversation_whenMessageNormalInserted_thenIsSelfDeleteFlagSetToFalse() = runTest {
        val message = OTHER_MESSAGE.copy(expireAfterMs = null)
        insertInitialData()
        messageDAO.insertOrIgnoreMessages(listOf(message))

        messageDAO.getNotificationMessage().let { notifications ->
            assertFalse { notifications.first().isSelfDelete }
        }
    }

    @Test
    fun givenConversationIsArchived_whenMessageInserted_thenDoNotNotify() = runTest {
        val message = OTHER_MESSAGE
        userDAO.upsertUsers(listOf(SELF_USER, OTHER_USER, OTHER_USER_2))
        conversationDAO.insertConversations(
            listOf(
                TEST_CONVERSATION_1.copy(
                    archived = true
                ),
                TEST_CONVERSATION_2
            )
        )

        messageDAO.insertOrIgnoreMessage(message)

        messageDAO.getNotificationMessage().let { notifications ->
            assertTrue { notifications.isEmpty() }
        }
    }

    private suspend fun doTheTest(
        mutedStatus: ConversationEntity.MutedStatus,
        status: UserAvailabilityStatusEntity,
        expectedResult: Boolean,
        message: MessageEntity,
        message2: MessageEntity? = null
    ) {
        insertInitialData()
        setConversationMutedStatus(message.conversationId, mutedStatus)
        setUserAvailability(status)
        message2?.let {
            messageDAO.insertOrIgnoreMessage(it)
        }
        messageDAO.insertOrIgnoreMessage(message)

        val result = messageDAO.needsToBeNotified(message.id, message.conversationId)
        assertEquals(expectedResult, result)
    }

    private suspend fun setUserAvailability(status: UserAvailabilityStatusEntity) {
        userDAO.updateUserAvailabilityStatus(SELF_USER_ID, status)
    }

    private suspend fun setConversationMutedStatus(conversationId: QualifiedIDEntity, mutedStatus: ConversationEntity.MutedStatus) {
        conversationDAO.updateConversationMutedStatus(conversationId, mutedStatus, Clock.System.now().toEpochMilliseconds())
    }

    override suspend fun insertInitialData() {
        super.insertInitialData()
        // Always insert original messages
    }

    private companion object {
        val ORIGINAL_MESSAGE_SENDER = OTHER_USER
        val SELF_MENTION = MessageEntity.Mention(
            start = 0, length = 9, userId = SELF_USER_ID
        )
        val OTHER_MENTION = MessageEntity.Mention(
            start = 10, length = 11, userId = OTHER_USER_2.id
        )

        const val OTHER_MESSAGE_CONTENT = "Something to think about"
        val OTHER_MESSAGE = newRegularMessageEntity(
            id = "OTHER_MESSAGE",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = ORIGINAL_MESSAGE_SENDER.id,
            content = MessageEntityContent.Text(OTHER_MESSAGE_CONTENT)
        )

        val OTHER_QUOTING_OTHERS = newRegularMessageEntity(
            id = "OTHER_QUOTING_OTHERS",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = ORIGINAL_MESSAGE_SENDER.id,
            content = MessageEntityContent.Text(
                "I'm quoting others", quotedMessageId = OTHER_MESSAGE.id
            )
        )

        val OTHER_MENTIONING_OTHERS = newRegularMessageEntity(
            id = "OTHER_MENTIONING_OTHERS",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = ORIGINAL_MESSAGE_SENDER.id,
            content = MessageEntityContent.Text(
                messageBody = "@$@${OTHER_USER_2.name}", mentions = listOf(OTHER_MENTION)
            )
        )

        val SELF_MESSAGE = newRegularMessageEntity(
            id = "SELF_MESSAGE",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = SELF_USER_ID,
            content = MessageEntityContent.Text(OTHER_MESSAGE_CONTENT)
        )

        val OTHER_QUOTING_SELF = newRegularMessageEntity(
            id = "OTHER_QUOTING_SELF",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = ORIGINAL_MESSAGE_SENDER.id,
            content = MessageEntityContent.Text(
                "I'm quoting selfUser", quotedMessageId = SELF_MESSAGE.id
            )
        )

        val OTHER_MENTIONING_SELF = newRegularMessageEntity(
            id = "OTHER_MENTIONING_SELF",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = ORIGINAL_MESSAGE_SENDER.id,
            content = MessageEntityContent.Text(
                messageBody = "@${SELF_USER.name} @${OTHER_USER_2.name}", mentions = listOf(SELF_MENTION)
            )
        )
    }
}
