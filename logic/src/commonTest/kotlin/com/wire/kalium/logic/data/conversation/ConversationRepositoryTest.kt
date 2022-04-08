package com.wire.kalium.logic.data.conversation

import app.cash.turbine.test

import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.conversation.ConvProtocol
import com.wire.kalium.network.api.conversation.ConversationApi
import com.wire.kalium.network.api.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.conversation.ConversationResponse
import com.wire.kalium.network.api.conversation.ConversationSelfMemberResponse
import com.wire.kalium.network.api.user.client.ClientApi
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.Member
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.verify
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
    private val mlsConversationRepository = mock(classOf<MLSConversationRepository>())

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
            mlsConversationRepository,
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
            .thenReturn(flowOf(listOf(Member(TestUser.ENTITY_ID))))

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
            .thenReturn(flowOf(listOf(Member(TestUser.ENTITY_ID))))

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

    @Test
    fun givenSelfUserBelongsToATeam_whenCallingCreateGroupConversation_thenConversationIsCreatedAtBackendAndPersisted() = runTest {

        given(conversationApi)
            .suspendFunction(conversationApi::createNewConversation)
            .whenInvokedWith(anything())
            .thenReturn(NetworkResponse.Success(CONVERSATION_RESPONSE, emptyMap(), 201))

        given(userRepository)
            .coroutine { userRepository.getSelfUser() }
            .then { flowOf(TestUser.SELF) }

        given(conversationDAO)
            .suspendFunction(conversationDAO::insertConversation)
            .whenInvokedWith(anything())
            .thenDoNothing()

        given(conversationDAO)
            .suspendFunction(conversationDAO::insertMembers)
            .whenInvokedWith(anything(), anything())
            .thenDoNothing()

        val result = conversationRepository.createGroupConversation(GROUP_NAME,
            listOf(Member((TestUser.USER_ID))),
            ConverationOptions(protocol = ConverationOptions.Protocol.PROTEUS))


        result.shouldSucceed { }

        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertConversation)
            .with(anything())
            .wasInvoked(once)

        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertMembers)
            .with(anything(), anything())
            .wasInvoked(once)
    }

    @Test
    fun givenSelfUserDoesNotBelongToATeam_whenCallingCreateGroupConversation_thenConversationIsCreatedAtBackendAndPersisted() = runTest {

        val selfUserWithoutTeam = TestUser.SELF.copy(team = null)

        given(conversationApi)
            .suspendFunction(conversationApi::createNewConversation)
            .whenInvokedWith(anything())
            .thenReturn(NetworkResponse.Success(CONVERSATION_RESPONSE, emptyMap(), 201))

        given(userRepository)
            .coroutine { userRepository.getSelfUser() }
            .then { flowOf(selfUserWithoutTeam) }

        given(conversationDAO)
            .suspendFunction(conversationDAO::insertConversation)
            .whenInvokedWith(anything())
            .thenDoNothing()

        given(conversationDAO)
            .suspendFunction(conversationDAO::insertMembers)
            .whenInvokedWith(anything(), anything())
            .thenDoNothing()

        val result = conversationRepository.createGroupConversation(GROUP_NAME,
            listOf(Member((TestUser.USER_ID))),
            ConverationOptions(protocol = ConverationOptions.Protocol.PROTEUS))


        result.shouldSucceed { }

        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertConversation)
            .with(anything())
            .wasInvoked(once)

        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertMembers)
            .with(anything(), anything())
            .wasInvoked(once)
    }

    @Test
    fun givenMLSProtocolIsUsed_whenCallingCreateGroupConversation_thenMLSGroupIsEstablished() = runTest {
        val conversationResponse = CONVERSATION_RESPONSE.copy(protocol = ConvProtocol.MLS)

        given(conversationApi)
            .suspendFunction(conversationApi::createNewConversation)
            .whenInvokedWith(anything())
            .thenReturn(NetworkResponse.Success(conversationResponse, emptyMap(), 201))

        given(userRepository)
            .coroutine { userRepository.getSelfUser() }
            .then { flowOf(TestUser.SELF) }

        given(userRepository)
            .coroutine { userRepository.getSelfUserId() }
            .then { TestUser.SELF.id }

        given(conversationDAO)
            .suspendFunction(conversationDAO::insertConversation)
            .whenInvokedWith(anything())
            .thenDoNothing()

        given(conversationDAO)
            .suspendFunction(conversationDAO::insertMembers)
            .whenInvokedWith(anything(), anything())
            .thenDoNothing()

        given(mlsConversationRepository)
            .suspendFunction(mlsConversationRepository::establishMLSGroup)
            .whenInvokedWith(anything())
            .then { Either.Right(Unit)}

        val result = conversationRepository.createGroupConversation(GROUP_NAME,
            listOf(Member((TestUser.USER_ID))),
            ConverationOptions(protocol = ConverationOptions.Protocol.MLS))

        result.shouldSucceed { }

        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertConversation)
            .with(anything())
            .wasInvoked(once)

        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertMembers)
            .with(anything(), anything())
            .wasInvoked(once)

        verify(mlsConversationRepository)
            .suspendFunction(mlsConversationRepository::establishMLSGroup)
            .with(anything())
            .wasInvoked(once)
    }

    companion object {
        const val GROUP_NAME = "Group Name"
        val CONVERSATION_RESPONSE = ConversationResponse(
            "creator",
            ConversationMembersResponse(
                ConversationSelfMemberResponse(MapperProvider.idMapper().toApiModel(TestUser.SELF.id)),
                emptyList()
            ),
            GROUP_NAME,
            TestConversation.NETWORK_ID,
            "group1",
            ConversationResponse.Type.GROUP,
            0,
            null,
            ConvProtocol.PROTEUS
        )

    }

}
