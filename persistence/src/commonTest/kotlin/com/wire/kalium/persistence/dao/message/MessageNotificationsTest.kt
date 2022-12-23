package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageNotificationsTest : BaseMessageTest() {

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
        conversationDAO.updateConversationMutedStatus(TEST_CONVERSATION_1.id, mutedStatus, Clock.System.now().toEpochMilliseconds())
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
