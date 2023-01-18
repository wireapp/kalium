package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

class MessageLatestTest : BaseMessageTest() {

    @Test
    fun givenMultipleMessagesFromOtherUsers_whenGettingLatestMessageFromOtherUsers_thenLatestIsReturned() = runTest {
        insertInitialData()
        val initialInstant = Clock.System.now()

        val mostRecentMessage = insertTestMessage(initialInstant + 30.days, OTHER_USER_2.id, TEST_CONVERSATION_2.id)

        insertTestMessage(initialInstant, OTHER_USER.id, TEST_CONVERSATION_1.id)
        insertTestMessage(initialInstant + 5.seconds, OTHER_USER_2.id, TEST_CONVERSATION_2.id)
        insertTestMessage(initialInstant + 15.seconds, OTHER_USER.id, TEST_CONVERSATION_1.id)

        val result = messageDAO.getLatestMessageFromOtherUsers()

        assertEquals(mostRecentMessage.id, result!!.id)
    }

    @Test
    fun givenMostRecentMessageIsFromSelfUser_whenGettingLatestMessageFromOtherUsers_thenLatestFromOthersIsReturned() = runTest {
        insertInitialData()
        val initialInstant = Clock.System.now()

        // Actual most recent
        insertTestMessage(initialInstant + 30.days, SELF_USER.id, TEST_CONVERSATION_2.id)

        insertTestMessage(initialInstant, OTHER_USER.id, TEST_CONVERSATION_1.id)
        insertTestMessage(initialInstant + 5.seconds, OTHER_USER_2.id, TEST_CONVERSATION_2.id)

        val mostRecentFromOthers = insertTestMessage(initialInstant + 15.seconds, OTHER_USER.id, TEST_CONVERSATION_1.id)

        val result = messageDAO.getLatestMessageFromOtherUsers()

        assertEquals(mostRecentFromOthers.id, result!!.id)
    }

    private suspend fun insertTestMessage(
        date: Instant,
        senderUserId: QualifiedIDEntity,
        conversationId: QualifiedIDEntity
    ) = newRegularMessageEntity(
        id = Random.nextBytes(10).decodeToString(),
        conversationId = conversationId,
        senderUserId = senderUserId,
        date = date
    ).also { messageDAO.insertOrIgnoreMessage(it) }

}
