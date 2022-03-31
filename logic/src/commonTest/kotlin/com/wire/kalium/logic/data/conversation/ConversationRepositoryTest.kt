package com.wire.kalium.logic.data.conversation

import app.cash.turbine.test
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.network.api.conversation.ConversationApi
import com.wire.kalium.network.api.user.client.ClientApi
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.MemberEntity
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ConversationRepositoryTest {

    @Mock
    private val userRepository = mock(UserRepository::class)

    @Mock
    private val conversationDAO = mock(ConversationDAO::class)

    @Mock
    private val conversationApi = mock(ConversationApi::class)

    @Mock
    private val clientApi = mock(ClientApi::class)

    private lateinit var conversationRepository: ConversationRepository

    @BeforeTest
    fun setup() {
        conversationRepository = ConversationDataSource(
            userRepository,
            conversationDAO,
            conversationApi,
            clientApi
        )
    }

    @Test
    fun givenConversationDaoReturnsAGroupConversation_whenGettingConversationDetailsById_thenReturnAGroupConversationDetails() = runTest {
        val conversationEntityFlow = flowOf(
            TestConversation.ENTITY.copy(type = ConversationEntity.Type.GROUP)
        )

        given(conversationDAO)
            .suspendFunction(conversationDAO::getConversationByQualifiedID)
            .whenInvokedWith(any())
            .thenReturn(conversationEntityFlow)

        conversationRepository.getConversationDetailsById(TestConversation.ID).test {
            assertIs<ConversationDetails.Group>(awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenConversationDaoReturnsASelfConversation_whenGettingConversationDetailsById_thenReturnASelfConversationDetails() = runTest {
        val conversationEntityFlow = flowOf(
            TestConversation.ENTITY.copy(type = ConversationEntity.Type.SELF)
        )

        given(conversationDAO)
            .suspendFunction(conversationDAO::getConversationByQualifiedID)
            .whenInvokedWith(any())
            .thenReturn(conversationEntityFlow)

        conversationRepository.getConversationDetailsById(TestConversation.ID).test {
            assertIs<ConversationDetails.Self>(awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenConversationDaoReturnsAOneOneConversation_whenGettingConversationDetailsById_thenReturnAOneOneConversationDetails() = runTest {
        val conversationId = TestConversation.ENTITY_ID
        val conversationEntityFlow = flowOf(
            TestConversation.ENTITY.copy(id = conversationId, type = ConversationEntity.Type.ONE_ON_ONE)
        )

        given(conversationDAO)
            .suspendFunction(conversationDAO::getConversationByQualifiedID)
            .whenInvokedWith(any())
            .thenReturn(conversationEntityFlow)

        given(userRepository)
            .suspendFunction(userRepository::getSelfUser)
            .whenInvoked()
            .thenReturn(flowOf(TestUser.SELF))

        given(conversationDAO)
            .suspendFunction(conversationDAO::getAllMembers)
            .whenInvokedWith(any())
            .thenReturn(flowOf(listOf(MemberEntity(TestUser.ENTITY_ID))))

        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(flowOf(TestUser.OTHER))

        conversationRepository.getConversationDetailsById(TestConversation.ID).test {
            assertIs<ConversationDetails.OneOne>(awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenOtherMemberOfOneOneConversationIsUpdated_whenGettingConversationDetailsById_thenReturnAOneOneConversationDetails() = runTest {
        val conversationId = TestConversation.ENTITY_ID
        val conversationEntityFlow = flowOf(
            TestConversation.ENTITY.copy(id = conversationId, type = ConversationEntity.Type.ONE_ON_ONE)
        )

        // The other user had a name, and then this name was updated.
        val otherUserDetailsSequence = listOf(TestUser.OTHER, TestUser.OTHER.copy(name = "Other Name Was Updated"))

        given(conversationDAO)
            .suspendFunction(conversationDAO::getConversationByQualifiedID)
            .whenInvokedWith(any())
            .thenReturn(conversationEntityFlow)

        given(userRepository)
            .suspendFunction(userRepository::getSelfUser)
            .whenInvoked()
            .thenReturn(flowOf(TestUser.SELF))

        given(conversationDAO)
            .suspendFunction(conversationDAO::getAllMembers)
            .whenInvokedWith(any())
            .thenReturn(flowOf(listOf(MemberEntity(TestUser.ENTITY_ID))))

        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(otherUserDetailsSequence.asFlow())

        conversationRepository.getConversationDetailsById(TestConversation.ID).test {
            val firstItem = awaitItem()
            assertIs<ConversationDetails.OneOne>(firstItem)
            assertEquals(otherUserDetailsSequence[0], firstItem.otherUser)

            val secondItem = awaitItem()
            assertIs<ConversationDetails.OneOne>(secondItem)
            assertEquals(otherUserDetailsSequence[1], secondItem.otherUser)

            awaitComplete()
        }
    }
}
