package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.ConversationDataSource
import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.util.DateTimeUtil
import io.mockative.Mock
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class NotificationDateTest {

    private val userDatabase = TestUserDatabase(SELF_USER_ID_ENTITY)

    @Mock
    val mlsClientProvider: MLSClientProvider = mock(MLSClientProvider::class)

    @Mock
    val selfTeamIdProvider: SelfTeamIdProvider = mock(SelfTeamIdProvider::class)

    @Mock
    val conversationApi: ConversationApi = mock(ConversationApi::class)

    @Mock
    private val clientApi = mock(ClientApi::class)

    private val conversationRepository =
        ConversationDataSource(
            TestUser.USER_ID,
            mlsClientProvider,
            selfTeamIdProvider,
            userDatabase.builder.conversationDAO,
            conversationApi,
            userDatabase.builder.messageDAO,
            userDatabase.builder.clientDAO,
            clientApi
        )

    private suspend fun insertTestData() {
        userDatabase.builder.userDAO.insertUser(TestUser.ENTITY.copy(id = SELF_USER_ID_ENTITY))
        userDatabase.builder.userDAO.insertUser(TestUser.ENTITY.copy(id = OTHER_USER_ID_ENTITY))
        userDatabase.builder.conversationDAO.insertConversations(listOf(CONVERSATION_ENTITY_1, CONVERSATION_ENTITY_2))
    }

    @Test
    fun givenCreatedConversation_whenGettingConversationData_thenLastNotificationDateIsNull() = runTest {
        insertTestData()

        val conversation = conversationRepository.getConversationById(CONVERSATION_ID_1)

        assertTrue {
            conversation != null
                    && conversation.lastNotificationDate == null
                    && conversation.lastModifiedDate != null
        }
    }

    @Test
    fun givenConversations_whenUpdatingLastNotificationDate_thenItIsUpdated() = runTest {
        insertTestData()

        val date = DateTimeUtil.currentInstant()
        conversationRepository.updateConversationNotificationDate(CONVERSATION_ID_1, date)

        val conversation1 = conversationRepository.getConversationById(CONVERSATION_ID_1)
        val conversation2 = conversationRepository.getConversationById(CONVERSATION_ID_2)
        assertTrue { conversation1 != null && conversation1.lastNotificationDate?.toInstant() == date }
        assertTrue { conversation2 != null && conversation2.lastNotificationDate == null }
    }

    @Test
    fun givenCreatedConversation_whenUpdatingLastNotificationDateForAllConversations_thenItIsUpdated() = runTest {
        insertTestData()

        val date = DateTimeUtil.currentInstant()
        conversationRepository.updateAllConversationsNotificationDate(date)

        val conversation1 = conversationRepository.getConversationById(CONVERSATION_ID_1)
        val conversation2 = conversationRepository.getConversationById(CONVERSATION_ID_2)
        assertTrue { conversation1 != null && conversation1.lastNotificationDate?.toInstant() == date }
        assertTrue { conversation2 != null && conversation2.lastNotificationDate?.toInstant() == date }
    }

    @Test
    fun givenConversationWithNotifyDateBiggerThenUpdateDate_whenUpdatingLastNotificationDateForAllConversations_thenItIsNotUpdated() =
        runTest {
            insertTestData()
            val lastNotificationDate3 = CONVERSATION_ENTITY_3.lastModifiedDate.plus(1.toDuration(DurationUnit.HOURS))
            userDatabase.builder.conversationDAO.insertConversation(
                CONVERSATION_ENTITY_3.copy(lastNotificationDate = lastNotificationDate3)
            )

            val date = DateTimeUtil.currentInstant()
            conversationRepository.updateAllConversationsNotificationDate(date)

            val conversation3 = conversationRepository.getConversationById(CONVERSATION_ID_3)
            assertTrue { conversation3 != null && conversation3.lastNotificationDate?.toInstant() == lastNotificationDate3 }
        }

    private companion object {
        val CONVERSATION_ID_1 = TestConversation.CONVERSATION.id
        val CONVERSATION_ID_2 = TestConversation.CONVERSATION.id.copy(value = "${TestConversation.CONVERSATION.id.value}_2")
        val CONVERSATION_ID_3 = TestConversation.CONVERSATION.id.copy(value = "${TestConversation.CONVERSATION.id.value}_3")
        val CONVERSATION_ENTITY_ID_1 = ConversationIDEntity(CONVERSATION_ID_1.value, CONVERSATION_ID_1.domain)
        val CONVERSATION_ENTITY_ID_2 = ConversationIDEntity(CONVERSATION_ID_2.value, CONVERSATION_ID_2.domain)
        val CONVERSATION_ENTITY_ID_3 = ConversationIDEntity(CONVERSATION_ID_3.value, CONVERSATION_ID_3.domain)
        val CONVERSATION_ENTITY_1 = TestConversation.ENTITY.copy(id = CONVERSATION_ENTITY_ID_1)
        val CONVERSATION_ENTITY_2 = TestConversation.ENTITY.copy(id = CONVERSATION_ENTITY_ID_2)
        val CONVERSATION_ENTITY_3 = TestConversation.ENTITY.copy(id = CONVERSATION_ENTITY_ID_3)

        val SELF_USER_ID = TestUser.SELF.id
        val SELF_USER_ID_ENTITY = UserIDEntity(SELF_USER_ID.value, SELF_USER_ID.domain)

        val OTHER_USER_ID = TestUser.OTHER_USER_ID
        val OTHER_USER_ID_ENTITY = UserIDEntity(OTHER_USER_ID.value, OTHER_USER_ID.domain)
    }
}
