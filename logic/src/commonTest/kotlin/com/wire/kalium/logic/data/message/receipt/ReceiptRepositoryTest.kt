package com.wire.kalium.logic.data.message.receipt

import app.cash.turbine.test
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ReceiptRepositoryTest {

    private val userDatabase = TestUserDatabase(TestUser.ENTITY_ID)
    private val receiptDAO = userDatabase.builder.receiptDAO
    private val conversationDao = userDatabase.builder.conversationDAO
    private val userDao = userDatabase.builder.userDAO
    private val messageDao = userDatabase.builder.messageDAO

    private val receiptRepository = ReceiptRepositoryImpl(receiptDAO)

    @AfterTest
    fun tearDown() {
        userDatabase.delete()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenMessageReadReceiptsWerePersisted_whenObservingMessageReceipts_thenShouldReturnReceiptsPreviouslyStored() = runTest {
        insertInitialData()

        val date = Clock.System.now()

        receiptRepository.persistReceipts(
            userId = TestUser.OTHER_USER_ID,
            conversationId = TEST_CONVERSATION_ID,
            date = date,
            type = ReceiptType.READ,
            messageIds = arrayOf(TEST_MESSAGE_ID)
        )

        launch(UnconfinedTestDispatcher(testScheduler)) {
            receiptRepository.observeMessageReceipts(
                conversationId = TEST_CONVERSATION_ID,
                messageId = TEST_MESSAGE_ID,
                type = ReceiptType.READ
            ).test {
                val result = awaitItem()
                assertTrue(result.size == 1)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenMessageReadReceiptsWerePersisted_whenObservingMessageReceipts_thenShouldReturnReceiptsPreviouslyStoredAndNameOrderedAlphabetically() =
        runTest {
            insertInitialData()

            val date = Clock.System.now()

            receiptRepository.persistReceipts(
                userId = TestUser.OTHER_USER_ID_2,
                conversationId = TEST_CONVERSATION_ID,
                date = date,
                type = ReceiptType.READ,
                messageIds = arrayOf(TEST_MESSAGE_ID)
            )

            receiptRepository.persistReceipts(
                userId = TestUser.OTHER_USER_ID,
                conversationId = TEST_CONVERSATION_ID,
                date = date,
                type = ReceiptType.READ,
                messageIds = arrayOf(TEST_MESSAGE_ID)
            )

            launch(UnconfinedTestDispatcher(testScheduler)) {
                receiptRepository.observeMessageReceipts(
                    conversationId = TEST_CONVERSATION_ID,
                    messageId = TEST_MESSAGE_ID,
                    type = ReceiptType.READ
                ).test {
                    val result = awaitItem()
                    assertTrue(result.size == 2)
                    assertTrue { awaitItem().first().userName == TEST_OTHER_USER_ENTITY.name }
                    assertTrue { awaitItem().last().userName == TEST_OTHER_USER_ENTITY_2.name }
                }
            }
        }

    suspend fun insertInitialData() {
        userDao.insertOrIgnoreUsers(listOf(TEST_SELF_USER_ENTITY, TEST_OTHER_USER_ENTITY, TEST_OTHER_USER_ENTITY_2))
        conversationDao.insertConversation(TEST_CONVERSATION_ENTITY)
        messageDao.insertMessage(TEST_MESSAGE_ENTITY)
    }

    private companion object {
        private val SELF_USER_ID = TestUser.USER_ID
        private val TEST_SELF_USER_ENTITY = TestUser.ENTITY.copy(
            id = QualifiedIDEntity(
                SELF_USER_ID.value,
                SELF_USER_ID.domain
            )
        )
        private val TEST_OTHER_USER_ENTITY = TestUser.ENTITY.copy(
            name = "AAA - First User Alphabetically",
            id = QualifiedIDEntity(
                TestUser.OTHER_USER_ID.value,
                TestUser.OTHER_USER_ID.domain
            )
        )
        private val TEST_OTHER_USER_ENTITY_2 = TestUser.ENTITY.copy(
            name = "ZZZ - Second User Alphabetically",
            id = QualifiedIDEntity(
                TestUser.OTHER_USER_ID_2.value,
                TestUser.OTHER_USER_ID_2.domain
            )
        )
        private val TEST_CONVERSATION_ID = TestConversation.ID
        private val TEST_CONVERSATION_ENTITY =
            TestConversation.ENTITY.copy(
                id = QualifiedIDEntity(
                    TEST_CONVERSATION_ID.value,
                    TEST_CONVERSATION_ID.domain
                )
            )
        private val TEST_MESSAGE_ID = TestMessage.TEST_MESSAGE_ID
        private val TEST_MESSAGE_ENTITY = TestMessage.ENTITY.copy(
            id = TEST_MESSAGE_ID,
            senderUserId = TEST_SELF_USER_ENTITY.id,
            conversationId = TEST_CONVERSATION_ENTITY.id
        )
    }
}
