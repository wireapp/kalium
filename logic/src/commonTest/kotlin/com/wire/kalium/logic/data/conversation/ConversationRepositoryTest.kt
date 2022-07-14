package com.wire.kalium.logic.data.conversation

import app.cash.turbine.test
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.conversation.AddParticipantResponse
import com.wire.kalium.network.api.conversation.ConvProtocol
import com.wire.kalium.network.api.conversation.ConversationApi
import com.wire.kalium.network.api.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.conversation.ConversationPagingResponse
import com.wire.kalium.network.api.conversation.ConversationResponse
import com.wire.kalium.network.api.conversation.ConversationResponseDTO
import com.wire.kalium.network.api.model.ConversationAccessDTO
import com.wire.kalium.network.api.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.user.client.ClientApi
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.Member
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import io.ktor.http.HttpStatusCode
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.fun2
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationRepositoryTest {

    @Mock
    private val userRepository = mock(UserRepository::class)

    @Mock
    private val mlsConversationRepository = mock(classOf<MLSConversationRepository>())

    @Mock
    private val conversationDAO = configure(mock(ConversationDAO::class)) { stubsUnitByDefault = true }

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
    fun givenNewConversationEvent_whenCallingInsertConversationFromEvent_thenConversationShouldBePersisted() = runTest {
        val event = Event.Conversation.NewConversation("id", TestConversation.ID, "time", CONVERSATION_RESPONSE)

        given(userRepository)
            .suspendFunction(userRepository::observeSelfUser)
            .whenInvoked()
            .thenReturn(flowOf(TestUser.SELF))

        conversationRepository.insertConversationFromEvent(event)

        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertConversations)
            .with(matching { conversations ->
                conversations.any { entity -> entity.id.value == CONVERSATION_RESPONSE.id.value }
            })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenNewConversationEventWithMlsConversation_whenCallingInsertConversation_thenMlsGroupExistenceShouldBeQueried() = runTest {
        val groupId = "group1"
        val event = Event.Conversation.NewConversation(
            "id",
            TestConversation.ID,
            "time",
            CONVERSATION_RESPONSE.copy(groupId = groupId, protocol = ConvProtocol.MLS)
        )
        val protocolInfo = ConversationEntity.ProtocolInfo.MLS(groupId, ConversationEntity.GroupState.ESTABLISHED)

        given(userRepository)
            .suspendFunction(userRepository::observeSelfUser)
            .whenInvoked()
            .thenReturn(flowOf(TestUser.SELF))

        given(mlsConversationRepository)
            .suspendFunction(mlsConversationRepository::hasEstablishedMLSGroup)
            .whenInvokedWith(anything())
            .thenReturn(Either.Right(true))

        conversationRepository.insertConversationFromEvent(event)

        verify(mlsConversationRepository)
            .suspendFunction(mlsConversationRepository::hasEstablishedMLSGroup)
            .with(eq(groupId))
            .wasInvoked(once)

        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertConversations)
            .with(matching { conversations ->
                conversations.any { entity ->
                    entity.id.value == CONVERSATION_RESPONSE.id.value && entity.protocolInfo == protocolInfo
                }
            })
            .wasInvoked(once)
    }

    @Test
    fun givenTwoPagesOfConversation_whenFetchingConversationsAndItsDetails_thenThePagesShouldBeAddedAndPersistOnlyFounds() =
        runTest {
            // given
            val response = ConversationPagingResponse(listOf(CONVERSATION_IDS_ONE, CONVERSATION_IDS_TWO), false, "")

            given(conversationApi)
                .suspendFunction(conversationApi::fetchConversationsIds)
                .whenInvokedWith(eq(null))
                .thenReturn(NetworkResponse.Success(response, emptyMap(), HttpStatusCode.OK.value))

            given(conversationApi)
                .suspendFunction(conversationApi::fetchConversationsListDetails)
                .whenInvokedWith(matching {
                    it.size == 2
                }).thenReturn(NetworkResponse.Success(CONVERSATION_RESPONSE_DTO, emptyMap(), HttpStatusCode.OK.value))

            given(userRepository)
                .suspendFunction(userRepository::observeSelfUser)
                .whenInvoked()
                .thenReturn(flowOf(TestUser.SELF))

            // when
            conversationRepository.fetchConversations()

            // then
            verify(conversationDAO)
                .suspendFunction(conversationDAO::insertConversations)
                .with(matching { list ->
                    list.any {
                        it.id.value == CONVERSATION_RESPONSE.id.value
                    }
                })
                .wasInvoked(exactly = once)
        }

    @Test
    fun givenConversationDaoReturnsAGroupConversation_whenGettingConversationDetailsById_thenReturnAGroupConversationDetails() = runTest {
        val conversationEntityFlow = flowOf(
            TestConversation.ENTITY.copy(type = ConversationEntity.Type.GROUP)
        )

        given(conversationDAO)
            .suspendFunction(conversationDAO::observeGetConversationByQualifiedID)
            .whenInvokedWith(any())
            .thenReturn(conversationEntityFlow)

        conversationRepository.observeConversationDetailsById(TestConversation.ID).test {
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
            .suspendFunction(conversationDAO::observeGetConversationByQualifiedID)
            .whenInvokedWith(any())
            .thenReturn(conversationEntityFlow)

        conversationRepository.observeConversationDetailsById(TestConversation.ID).test {
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
            .suspendFunction(conversationDAO::observeGetConversationByQualifiedID)
            .whenInvokedWith(any())
            .thenReturn(conversationEntityFlow)

        given(userRepository)
            .suspendFunction(userRepository::observeSelfUser)
            .whenInvoked()
            .thenReturn(flowOf(TestUser.SELF))

        given(conversationDAO)
            .suspendFunction(conversationDAO::getAllMembers)
            .whenInvokedWith(any())
            .thenReturn(flowOf(listOf(Member(TestUser.ENTITY_ID, Member.Role.Member))))

        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(flowOf(TestUser.OTHER))

        conversationRepository.observeConversationDetailsById(TestConversation.ID).test {
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
            .suspendFunction(conversationDAO::observeGetConversationByQualifiedID)
            .whenInvokedWith(any())
            .thenReturn(conversationEntityFlow)

        given(userRepository)
            .suspendFunction(userRepository::observeSelfUser)
            .whenInvoked()
            .thenReturn(flowOf(TestUser.SELF))

        given(conversationDAO)
            .suspendFunction(conversationDAO::getAllMembers)
            .whenInvokedWith(any())
            .thenReturn(flowOf(listOf(Member(TestUser.ENTITY_ID, Member.Role.Member))))

        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(otherUserDetailsSequence.asFlow())

        conversationRepository.observeConversationDetailsById(TestConversation.ID).test {
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
            .coroutine { userRepository.observeSelfUser() }
            .then { flowOf(TestUser.SELF) }

        given(conversationDAO)
            .suspendFunction(conversationDAO::insertConversation)
            .whenInvokedWith(anything())
            .thenDoNothing()

        given(conversationDAO)
            .suspendFunction(conversationDAO::insertMembers, fun2<List<Member>, QualifiedIDEntity>())
            .whenInvokedWith(anything(), anything())
            .thenDoNothing()

        val result = conversationRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID),
            ConversationOptions(protocol = ConversationOptions.Protocol.PROTEUS)
        )

        result.shouldSucceed { }

        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertConversation)
            .with(anything())
            .wasInvoked(once)

        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertMembers, fun2<List<Member>, QualifiedIDEntity>())
            .with(anything(), anything())
            .wasInvoked(once)
    }

    @Test
    fun givenSelfUserDoesNotBelongToATeam_whenCallingCreateGroupConversation_thenConversationIsCreatedAtBackendAndPersisted() = runTest {

        val selfUserWithoutTeam = TestUser.SELF.copy(teamId = null)

        given(conversationApi)
            .suspendFunction(conversationApi::createNewConversation)
            .whenInvokedWith(anything())
            .thenReturn(NetworkResponse.Success(CONVERSATION_RESPONSE, emptyMap(), 201))

        given(userRepository)
            .coroutine { userRepository.observeSelfUser() }
            .then { flowOf(selfUserWithoutTeam) }

        given(conversationDAO)
            .suspendFunction(conversationDAO::insertConversation)
            .whenInvokedWith(anything())
            .thenDoNothing()

        given(conversationDAO)
            .suspendFunction(conversationDAO::insertMembers, fun2<List<Member>, QualifiedIDEntity>())
            .whenInvokedWith(anything(), anything())
            .thenDoNothing()

        val result = conversationRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID),
            ConversationOptions(protocol = ConversationOptions.Protocol.PROTEUS)
        )

        result.shouldSucceed { }

        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertConversation)
            .with(anything())
            .wasInvoked(once)

        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertMembers, fun2<List<Member>, QualifiedIDEntity>())
            .with(anything(), anything())
            .wasInvoked(once)
    }

    // TODO: enable the tests once the issue with creating MLS conversations is solved
    @Ignore
    @Test
    fun givenMLSProtocolIsUsed_whenCallingCreateGroupConversation_thenMLSGroupIsEstablished() = runTest {
        val conversationResponse = CONVERSATION_RESPONSE.copy(protocol = ConvProtocol.MLS)

        given(conversationApi)
            .suspendFunction(conversationApi::createNewConversation)
            .whenInvokedWith(anything())
            .thenReturn(NetworkResponse.Success(conversationResponse, emptyMap(), 201))

        given(userRepository)
            .coroutine { userRepository.observeSelfUser() }
            .then { flowOf(TestUser.SELF) }

        given(userRepository)
            .coroutine { userRepository.getSelfUserId() }
            .then { TestUser.SELF.id }

        given(conversationDAO)
            .suspendFunction(conversationDAO::insertConversation)
            .whenInvokedWith(anything())
            .thenDoNothing()

        given(conversationDAO)
            .suspendFunction(conversationDAO::insertMembers, fun2<List<Member>, QualifiedIDEntity>())
            .whenInvokedWith(any(), any())
            .thenDoNothing()

        given(mlsConversationRepository)
            .suspendFunction(mlsConversationRepository::establishMLSGroup)
            .whenInvokedWith(anything())
            .then { Either.Right(Unit) }

        val result = conversationRepository.createGroupConversation(
            GROUP_NAME,
            listOf(TestUser.USER_ID),
            ConversationOptions(protocol = ConversationOptions.Protocol.MLS)
        )

        result.shouldSucceed { }

        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertConversation)
            .with(anything())
            .wasInvoked(once)

        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertMembers, fun2<List<Member>, QualifiedIDEntity>())
            .with(anything(), anything())
            .wasInvoked(once)

        verify(mlsConversationRepository)
            .suspendFunction(mlsConversationRepository::establishMLSGroup)
            .with(anything())
            .wasInvoked(once)
    }

    @Test
    fun givenUserHasKnownContactAndConversation_WhenGettingConversationDetailsByExistingConversation_ReturnTheCorrectConversation() =
        runTest {
            // given
            given(conversationDAO)
                .suspendFunction(conversationDAO::getAllConversationWithOtherUser)
                .whenInvokedWith(anything())
                .then { listOf(CONVERSATION_ENTITY) }

            given(userRepository)
                .coroutine { userRepository.observeSelfUser() }
                .then { flowOf(TestUser.SELF) }

            given(userRepository)
                .suspendFunction(userRepository::getKnownUser)
                .whenInvokedWith(any())
                .thenReturn(flowOf(TestUser.OTHER))

            // when
            val result = conversationRepository.getOneToOneConversationDetailsByUserId(OTHER_USER_ID)
            // then
            assertIs<Either.Right<ConversationDetails.OneOne>>(result)
        }

    @Test
    fun givenAWantToMuteAConversation_whenCallingUpdateMutedStatus_thenShouldDelegateCallToConversationApi() = runTest {
        given(conversationApi)
            .suspendFunction(conversationApi::updateConversationMemberState)
            .whenInvokedWith(any(), any())
            .thenReturn(NetworkResponse.Success(Unit, mapOf(), HttpStatusCode.OK.value))

        given(conversationDAO)
            .suspendFunction(conversationDAO::updateConversationMutedStatus)
            .whenInvokedWith(any(), any(), any())
            .thenReturn(Unit)

        conversationRepository.updateMutedStatus(
            TestConversation.ID,
            MutedConversationStatus.AllMuted,
            Clock.System.now().toEpochMilliseconds()
        )

        verify(conversationApi)
            .suspendFunction(conversationApi::updateConversationMemberState)
            .with(any(), any())
            .wasInvoked(exactly = once)

        verify(conversationDAO)
            .suspendFunction(conversationDAO::updateConversationMutedStatus)
            .with(any(), any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationExists_whenFetchingConversationIfUnknown_thenShouldNotFetchFromApi() = runTest {
        val conversationId = TestConversation.ID
        given(conversationDAO)
            .suspendFunction(conversationDAO::getConversationByQualifiedID)
            .whenInvokedWith(any())
            .thenReturn(TestConversation.ENTITY)

        conversationRepository.fetchConversationIfUnknown(conversationId)

        verify(conversationApi)
            .suspendFunction(conversationApi::fetchConversationDetails)
            .with(eq(ConversationId(value = conversationId.value, domain = conversationId.domain)))
            .wasNotInvoked()
    }

    @Test
    fun givenAConversationExists_whenFetchingConversationIfUnknown_thenShouldSucceed() = runTest {
        val conversationId = TestConversation.ID
        given(conversationDAO)
            .suspendFunction(conversationDAO::getConversationByQualifiedID)
            .whenInvokedWith(any())
            .thenReturn(TestConversation.ENTITY)

        conversationRepository.fetchConversationIfUnknown(conversationId)
            .shouldSucceed()
    }

    @Test
    fun givenAConversationDoesNotExist_whenFetchingConversationIfUnknown_thenShouldFetchFromAPI() = runTest {
        val conversationId = TestConversation.ID
        given(conversationDAO)
            .suspendFunction(conversationDAO::getConversationByQualifiedID)
            .whenInvokedWith(any())
            .thenReturn(null)

        given(userRepository)
            .coroutine { userRepository.getSelfUser() }
            .then { TestUser.SELF }

        given(conversationApi)
            .suspendFunction(conversationApi::fetchConversationDetails)
            .whenInvokedWith(eq(ConversationId(value = conversationId.value, domain = conversationId.domain)))
            .thenReturn(NetworkResponse.Success(TestConversation.CONVERSATION_RESPONSE, mapOf(), HttpStatusCode.OK.value))

        conversationRepository.fetchConversationIfUnknown(conversationId)
            .shouldSucceed()

        verify(conversationApi)
            .suspendFunction(conversationApi::fetchConversationDetails)
            .with(eq(ConversationId(value = conversationId.value, domain = conversationId.domain)))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationDoesNotExistAndAPISucceeds_whenFetchingConversationIfUnknown_thenShouldSucceed() = runTest {
        val conversationId = TestConversation.ID
        given(conversationDAO)
            .suspendFunction(conversationDAO::getConversationByQualifiedID)
            .whenInvokedWith(any())
            .thenReturn(null)

        given(userRepository)
            .coroutine { userRepository.getSelfUser() }
            .then { TestUser.SELF }

        given(conversationApi)
            .suspendFunction(conversationApi::fetchConversationDetails)
            .whenInvokedWith(eq(ConversationId(value = conversationId.value, domain = conversationId.domain)))
            .thenReturn(NetworkResponse.Success(TestConversation.CONVERSATION_RESPONSE, mapOf(), HttpStatusCode.OK.value))

        conversationRepository.fetchConversationIfUnknown(conversationId)
            .shouldSucceed()
    }

    @Test
    fun givenAConversationAndAPISucceeds_whenAddingMembersToConversation_thenShouldSucceed() = runTest {
        val conversationId = TestConversation.ID
        given(conversationApi)
            .suspendFunction(conversationApi::addParticipant)
            .whenInvokedWith(any(), any())
            .thenReturn(
                NetworkResponse.Success(
                    TestConversation.ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE,
                    mapOf(),
                    HttpStatusCode.OK.value
                )
            )
        given(conversationDAO)
            .suspendFunction(conversationDAO::insertMembers, fun2<List<Member>, QualifiedIDEntity>())
            .whenInvokedWith(any(), any())
            .thenDoNothing()
        given(userRepository)
            .suspendFunction(userRepository::fetchUsersIfUnknownByIds)
            .whenInvokedWith(any())
            .thenReturn(Either.Right(Unit))

        conversationRepository.addMembers(listOf(TestConversation.USER_1), conversationId)
            .shouldSucceed()

        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertMembers, fun2<List<Member>, QualifiedIDEntity>())
            .with(anything(), anything())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationAndAPIFailed_whenAddingMembersToConversation_thenShouldNotSucceed() = runTest {
        val conversationId = TestConversation.ID
        given(conversationApi)
            .suspendFunction(conversationApi::addParticipant)
            .whenInvokedWith(any(), any())
            .thenReturn(
                NetworkResponse.Success(
                    AddParticipantResponse.ConversationUnchanged,
                    mapOf(),
                    HttpStatusCode.NoContent.value
                )
            )

        conversationRepository.addMembers(listOf(TestConversation.USER_1), conversationId)
            .shouldSucceed()

        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertMembers, fun2<List<Member>, QualifiedIDEntity>())
            .with(any(), any())
            .wasNotInvoked()
    }

    companion object {
        const val GROUP_NAME = "Group Name"

        val CONVERSATION_IDS_ONE =
            ConversationId("someValue1", "someDomain1")

        val CONVERSATION_IDS_TWO =
            ConversationId("someValue2", "someDomain2")

        val CONVERSATION_RESPONSE = ConversationResponse(
            "creator",
            ConversationMembersResponse(
                ConversationMemberDTO.Self(MapperProvider.idMapper().toApiModel(TestUser.SELF.id), "wire_member"),
                emptyList()
            ),
            GROUP_NAME,
            TestConversation.NETWORK_ID,
            null,
            ConversationResponse.Type.GROUP,
            0,
            null,
            ConvProtocol.PROTEUS,
            lastEventTime = "2022-03-30T15:36:00.000Z",
            access = setOf(ConversationAccessDTO.INVITE, ConversationAccessDTO.CODE),
            accessRole = setOf(
                ConversationAccessRoleDTO.GUEST,
                ConversationAccessRoleDTO.TEAM_MEMBER,
                ConversationAccessRoleDTO.NON_TEAM_MEMBER
            )
        )

        val CONVERSATION_RESPONSE_DTO = ConversationResponseDTO(
            conversationsFound = listOf(CONVERSATION_RESPONSE),
            conversationsFailed = listOf(CONVERSATION_RESPONSE.copy(id = ConversationId("failedId", "someDomain"))),
            conversationsNotFound = emptyList()
        )

        val OTHER_USER_ID = UserId("otherValue", "domain")

        val CONVERSATION_ENTITY = ConversationEntity(
            id = QualifiedIDEntity(
                value = "testValue",
                domain = "testDomain",
            ),
            name = null,
            type = ConversationEntity.Type.ONE_ON_ONE,
            teamId = null,
            protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
            lastModifiedDate = "2022-03-30T15:36:00.000Z",
            lastNotificationDate = null,
            access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
            accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER)
        )
    }
}
