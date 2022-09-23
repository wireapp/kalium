package com.wire.kalium.logic.data.conversation

import app.cash.turbine.test
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.PersistenceQualifiedId
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.TimeParser
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.base.authenticated.conversation.ConvProtocol.MLS
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberRemovedDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationPagingResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponseDTO
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationAccessInfoDTO
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationMemberRoleDTO
import com.wire.kalium.network.api.base.authenticated.conversation.model.UpdateConversationAccessResponse
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.model.ConversationAccessDTO
import com.wire.kalium.network.api.base.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.wire.kalium.network.api.base.model.ConversationId as ConversationIdDTO
import com.wire.kalium.persistence.dao.Member as MemberEntity

@Suppress("LargeClass")
@OptIn(ExperimentalCoroutinesApi::class)
// TODO: Refactor using Arrangement pattern
class ConversationRepositoryTest {

    @Mock
    private val userRepository = mock(UserRepository::class)

    @Mock
    private val mlsConversationRepository = mock(classOf<MLSConversationRepository>())

    @Mock
    private val conversationDAO = configure(mock(ConversationDAO::class)) { stubsUnitByDefault = true }

    @Mock
    private val messageDAO = configure(mock(MessageDAO::class)) { stubsUnitByDefault = true }

    @Mock
    private val conversationApi = mock(ConversationApi::class)

    @Mock
    private val clientDao = mock(ClientDAO::class)

    @Mock
    private val clientApi = mock(ClientApi::class)

    @Mock
    private val timeParser: TimeParser = mock(TimeParser::class)

    private lateinit var conversationRepository: ConversationRepository

    @BeforeTest
    fun setup() {
        conversationRepository = ConversationDataSource(
            userRepository,
            mlsConversationRepository,
            conversationDAO,
            conversationApi,
            messageDAO,
            clientDao,
            clientApi,
            timeParser,
            TestUser.SELF.id
        )
    }

    @Test
    fun givenNewConversationEvent_whenCallingInsertConversationFromEvent_thenConversationShouldBePersisted() = runTest {
        val event = Event.Conversation.NewConversation("id", TestConversation.ID, "time", CONVERSATION_RESPONSE)
        val selfUserFlow = flowOf(TestUser.SELF)
        val (arrangement, conversationRepository) = Arrangement()
            .withSelfUserFlow(selfUserFlow)
            .withInsertConversations()
            .arrange()

        conversationRepository.insertConversationFromEvent(event)

        with(arrangement) {
            verify(conversationDAO)
                .suspendFunction(conversationDAO::insertConversations)
                .with(
                    matching { conversations ->
                        conversations.any { entity -> entity.id.value == CONVERSATION_RESPONSE.id.value }
                    }
                )
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenNewConversationEventWithMlsConversation_whenCallingInsertConversation_thenMlsGroupExistenceShouldBeQueried() = runTest {
        val event = Event.Conversation.NewConversation(
            "id",
            TestConversation.ID,
            "time",
            CONVERSATION_RESPONSE.copy(
                groupId = RAW_GROUP_ID,
                protocol = MLS,
                mlsCipherSuiteTag = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519.cipherSuiteTag
            )
        )
        val protocolInfo = ConversationEntity.ProtocolInfo.MLS(
            RAW_GROUP_ID,
            ConversationEntity.GroupState.ESTABLISHED,
            0UL,
            Instant.parse("2021-03-30T15:36:00.000Z"),
            cipherSuite = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        )

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
            .with(eq(GROUP_ID))
            .wasInvoked(once)

        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertConversations)
            .with(
                matching { conversations ->
                    conversations.any { entity ->
                        entity.id.value == CONVERSATION_RESPONSE.id.value && entity.protocolInfo == protocolInfo.copy(
                            keyingMaterialLastUpdate = (entity.protocolInfo as ConversationEntity.ProtocolInfo.MLS).keyingMaterialLastUpdate
                        )
                    }
                }
            )
            .wasInvoked(once)
    }

    @Test
    fun givenTwoPagesOfConversation_whenFetchingConversationsAndItsDetails_thenThePagesShouldBeAddedAndPersistOnlyFounds() =
        runTest {
            // given
            val response = ConversationPagingResponse(listOf(CONVERSATION_IDS_DTO_ONE, CONVERSATION_IDS_DTO_TWO), false, "")

            given(conversationApi)
                .suspendFunction(conversationApi::fetchConversationsIds)
                .whenInvokedWith(eq(null))
                .thenReturn(NetworkResponse.Success(response, emptyMap(), HttpStatusCode.OK.value))

            given(conversationApi)
                .suspendFunction(conversationApi::fetchConversationsListDetails)
                .whenInvokedWith(
                    matching {
                        it.size == 2
                    }
                ).thenReturn(NetworkResponse.Success(CONVERSATION_RESPONSE_DTO, emptyMap(), HttpStatusCode.OK.value))

            given(userRepository)
                .suspendFunction(userRepository::observeSelfUser)
                .whenInvoked()
                .thenReturn(flowOf(TestUser.SELF))

            // when
            conversationRepository.fetchConversations()

            // then
            verify(conversationDAO)
                .suspendFunction(conversationDAO::insertConversations)
                .with(
                    matching { list ->
                        list.any {
                            it.id.value == CONVERSATION_RESPONSE.id.value
                        }
                    }
                )
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

        given(timeParser)
            .function(timeParser::isTimeBefore)
            .whenInvokedWith(any(), any())
            .thenReturn(true)

        given(messageDAO)
            .suspendFunction(messageDAO::observeUnreadMessageCount)
            .whenInvokedWith(any())
            .thenReturn(flowOf(10))

        given(messageDAO)
            .suspendFunction(messageDAO::observeUnreadMentionsCount)
            .whenInvokedWith(any())
            .thenReturn(flowOf(10))

        given(messageDAO)
            .suspendFunction(messageDAO::observeLastUnreadMessage)
            .whenInvokedWith(any())
            .thenReturn(flowOf(TEST_MESSAGE_ENTITY))

        given(userRepository)
            .coroutine { userRepository.observeSelfUser() }
            .then { flowOf(TestUser.SELF) }

        conversationRepository.observeConversationDetailsById(TestConversation.ID).test {
            assertIs<Either.Right<ConversationDetails.Group>>(awaitItem())
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
            assertIs<Either.Right<ConversationDetails.Self>>(awaitItem())
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
            .thenReturn(flowOf(listOf(MemberEntity(TestUser.ENTITY_ID, MemberEntity.Role.Member))))

        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(flowOf(TestUser.OTHER))

        given(timeParser)
            .function(timeParser::isTimeBefore)
            .whenInvokedWith(any(), any())
            .thenReturn(true)

        given(messageDAO)
            .suspendFunction(messageDAO::observeUnreadMessageCount)
            .whenInvokedWith(any())
            .thenReturn(flowOf(10))

        given(messageDAO)
            .suspendFunction(messageDAO::observeUnreadMentionsCount)
            .whenInvokedWith(any())
            .thenReturn(flowOf(10))

        given(messageDAO)
            .suspendFunction(messageDAO::observeLastUnreadMessage)
            .whenInvokedWith(any())
            .thenReturn(flowOf(TEST_MESSAGE_ENTITY))

        conversationRepository.observeConversationDetailsById(TestConversation.ID).test {
            assertIs<Either.Right<ConversationDetails.OneOne>>(awaitItem())
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
        val otherUserDetailsChannel = Channel<OtherUser>(Channel.UNLIMITED)

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
            .thenReturn(flowOf(listOf(MemberEntity(TestUser.ENTITY_ID, MemberEntity.Role.Member))))

        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(otherUserDetailsChannel.consumeAsFlow())

        given(timeParser)
            .function(timeParser::isTimeBefore)
            .whenInvokedWith(any(), any())
            .thenReturn(true)

        given(messageDAO)
            .suspendFunction(messageDAO::observeUnreadMessageCount)
            .whenInvokedWith(any())
            .thenReturn(flowOf(10))

        given(messageDAO)
            .suspendFunction(messageDAO::observeUnreadMentionsCount)
            .whenInvokedWith(any())
            .thenReturn(flowOf(10))

        given(messageDAO)
            .suspendFunction(messageDAO::observeLastUnreadMessage)
            .whenInvokedWith(any())
            .thenReturn(flowOf(TEST_MESSAGE_ENTITY))

        conversationRepository.observeConversationDetailsById(TestConversation.ID).test {
            otherUserDetailsChannel.send(otherUserDetailsSequence[0])
            val firstItem = awaitItem()
            assertIs<Either.Right<ConversationDetails.OneOne>>(firstItem)
            assertEquals(otherUserDetailsSequence[0], firstItem.value.otherUser)

            otherUserDetailsChannel.send(otherUserDetailsSequence[1])
            val secondItem = awaitItem()
            assertIs<Either.Right<ConversationDetails.OneOne>>(secondItem)
            assertEquals(otherUserDetailsSequence[1], secondItem.value.otherUser)

            otherUserDetailsChannel.close()
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
            .suspendFunction(conversationDAO::insertMembersWithQualifiedId, fun2<List<MemberEntity>, QualifiedIDEntity>())
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
            .suspendFunction(conversationDAO::insertMembersWithQualifiedId, fun2<List<MemberEntity>, QualifiedIDEntity>())
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
            .suspendFunction(conversationDAO::insertMembersWithQualifiedId, fun2<List<MemberEntity>, QualifiedIDEntity>())
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
            .suspendFunction(conversationDAO::insertMembersWithQualifiedId, fun2<List<MemberEntity>, QualifiedIDEntity>())
            .with(anything(), anything())
            .wasInvoked(once)
    }

    @Test
    fun givenMLSProtocolIsUsed_whenCallingCreateGroupConversation_thenMLSGroupIsEstablished() = runTest {
        val conversationResponse = CONVERSATION_RESPONSE.copy(protocol = MLS)

        given(conversationApi)
            .suspendFunction(conversationApi::createNewConversation)
            .whenInvokedWith(anything())
            .thenReturn(NetworkResponse.Success(conversationResponse, emptyMap(), 201))

        given(userRepository)
            .coroutine { userRepository.observeSelfUser() }
            .then { flowOf(TestUser.SELF) }

        given(conversationDAO)
            .suspendFunction(conversationDAO::insertConversation)
            .whenInvokedWith(anything())
            .thenDoNothing()

        given(conversationDAO)
            .suspendFunction(conversationDAO::insertMembersWithQualifiedId, fun2<List<MemberEntity>, QualifiedIDEntity>())
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
            .suspendFunction(conversationDAO::insertMembersWithQualifiedId, fun2<List<MemberEntity>, QualifiedIDEntity>())
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
                .suspendFunction(conversationDAO::getConversationWithOtherUser)
                .whenInvokedWith(anything())
                .then { CONVERSATION_ENTITY }

            given(userRepository)
                .coroutine { userRepository.observeSelfUser() }
                .then { flowOf(TestUser.SELF) }

            given(userRepository)
                .suspendFunction(userRepository::getKnownUser)
                .whenInvokedWith(any())
                .thenReturn(flowOf(TestUser.OTHER))

            // when
            val result = conversationRepository.getOneToOneConversationWithOtherUser(OTHER_USER_ID)
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
        val conversationIdDTO = ConversationIdDTO(value = conversationId.value, domain = conversationId.domain)
        given(conversationDAO)
            .suspendFunction(conversationDAO::getConversationByQualifiedID)
            .whenInvokedWith(any())
            .thenReturn(null)

        given(userRepository)
            .coroutine { userRepository.getSelfUser() }
            .then { TestUser.SELF }

        given(conversationApi)
            .suspendFunction(conversationApi::fetchConversationDetails)
            .whenInvokedWith(eq(conversationIdDTO))
            .thenReturn(NetworkResponse.Success(TestConversation.CONVERSATION_RESPONSE, mapOf(), HttpStatusCode.OK.value))

        conversationRepository.fetchConversationIfUnknown(conversationId)
            .shouldSucceed()

        verify(conversationApi)
            .suspendFunction(conversationApi::fetchConversationDetails)
            .with(eq(conversationIdDTO))
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
            .whenInvokedWith(eq(ConversationIdDTO(value = conversationId.value, domain = conversationId.domain)))
            .thenReturn(NetworkResponse.Success(TestConversation.CONVERSATION_RESPONSE, mapOf(), HttpStatusCode.OK.value))

        conversationRepository.fetchConversationIfUnknown(conversationId)
            .shouldSucceed()
    }

    @Test
    fun givenAConversationAndAPISucceeds_whenAddingMembersToConversation_thenShouldSucceed() = runTest {
        val conversationId = TestConversation.ID
        given(conversationApi)
            .suspendFunction(conversationApi::addMember)
            .whenInvokedWith(any(), any())
            .thenReturn(
                NetworkResponse.Success(
                    TestConversation.ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE,
                    mapOf(),
                    HttpStatusCode.OK.value
                )
            )
        given(conversationDAO)
            .suspendFunction(conversationDAO::insertMembersWithQualifiedId, fun2<List<MemberEntity>, QualifiedIDEntity>())
            .whenInvokedWith(any(), any())
            .thenDoNothing()
        given(userRepository)
            .suspendFunction(userRepository::fetchUsersIfUnknownByIds)
            .whenInvokedWith(any())
            .thenReturn(Either.Right(Unit))

        conversationRepository.addMembers(listOf(TestConversation.USER_1), conversationId)
            .shouldSucceed()

        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertMembersWithQualifiedId, fun2<List<MemberEntity>, QualifiedIDEntity>())
            .with(anything(), anything())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationAndAPIFailed_whenAddingMembersToConversation_thenShouldNotSucceed() = runTest {
        val conversationId = TestConversation.ID
        given(conversationApi)
            .suspendFunction(conversationApi::addMember)
            .whenInvokedWith(any(), any())
            .thenReturn(
                NetworkResponse.Error(
                    KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))
                )
            )

        conversationRepository.addMembers(listOf(TestConversation.USER_1), conversationId)
            .shouldFail()

        verify(conversationDAO)
            .suspendFunction(conversationDAO::insertMembersWithQualifiedId, fun2<List<MemberEntity>, QualifiedIDEntity>())
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenAConversationAndAPISucceedsWithChange_whenRemovingMemberFromConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationRepository) = Arrangement()
            .withConversationProtocolIs(PROTEUS_PROTOCOL_INFO)
            .withDeleteMemberAPISucceedChanged()
            .withSuccessfulMemberDeletion()
            .arrange()

        conversationRepository.deleteMember(TestConversation.USER_1, TestConversation.ID)
            .shouldSucceed { it is MemberChangeResult.Changed }

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::deleteMemberByQualifiedID)
            .with(anything(), anything())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationAndAPISucceedsWithoutChange_whenRemovingMemberFromConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationRepository) = Arrangement()
            .withConversationProtocolIs(PROTEUS_PROTOCOL_INFO)
            .withDeleteMemberAPISucceedUnchanged()
            .withSuccessfulMemberDeletion()
            .arrange()

        conversationRepository.deleteMember(TestConversation.USER_1, TestConversation.ID)
            .shouldSucceed { it is MemberChangeResult.Unchanged }

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::deleteMemberByQualifiedID)
            .with(anything(), anything())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationAndAPIFailed_whenRemovingMemberFromConversation_thenShouldFail() = runTest {
        val (arrangement, conversationRepository) = Arrangement()
            .withConversationProtocolIs(PROTEUS_PROTOCOL_INFO)
            .withDeleteMemberAPIFailed()
            .withSuccessfulMemberDeletion()
            .arrange()

        conversationRepository.deleteMember(TestConversation.USER_1, TestConversation.ID)
            .shouldFail()

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::deleteMemberByQualifiedID)
            .with(anything(), anything())
            .wasNotInvoked()
    }

    @Test
    fun givenAnMLSConversationAndAPISucceeds_whenRemovingLeavingConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationRepository) = Arrangement()
            .withConversationProtocolIs(MLS_PROTOCOL_INFO)
            .withDeleteMemberAPISucceedChanged()
            .withSuccessfulMemberDeletion()
            .withSuccessfulLeaveMLSGroup()
            .arrange()

        conversationRepository.deleteMember(TestUser.SELF.id, TestConversation.ID)
            .shouldSucceed { it is MemberChangeResult.Changed }

        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::deleteMemberByQualifiedID)
            .with(anything(), anything())
            .wasInvoked(exactly = once)
        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::leaveGroup)
            .with(eq(GROUP_ID))
            .wasInvoked(exactly = once)
        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::removeMembersFromMLSGroup)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenAnMLSConversationAndAPISucceeds_whenRemoveMemberFromConversation_thenShouldSucceed() = runTest {
        val (arrangement, conversationRepository) = Arrangement()
            .withConversationProtocolIs(MLS_PROTOCOL_INFO)
            .withDeleteMemberAPISucceedChanged()
            .withSuccessfulMemberDeletion()
            .withSuccessfulRemoveMemberFromMLSGroup()
            .arrange()

        conversationRepository.deleteMember(TestConversation.USER_1, TestConversation.ID)
            .shouldSucceed { it is MemberChangeResult.Changed }

        // this function called in the mlsRepo
        verify(arrangement.conversationDAO)
            .suspendFunction(arrangement.conversationDAO::deleteMemberByQualifiedID)
            .with(anything(), anything())
            .wasNotInvoked()
        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::removeMembersFromMLSGroup)
            .with(eq(GROUP_ID), eq(listOf(TestConversation.USER_1)))
            .wasInvoked(exactly = once)
        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::leaveGroup)
            .with(any())
            .wasNotInvoked()
    }

    @Suppress("LongMethod")
    @Test
    fun givenUpdateAccessRoleSuccess_whenUpdatingConversationAccessInfo_thenTheNewAccessSettingsAreUpdatedLocally() = runTest {

        val conversationIdDTO = ConversationIdDTO("conv_id", "conv_domain")
        val newAccessIndoDTO = ConversationAccessInfoDTO(
            accessRole = setOf(
                ConversationAccessRoleDTO.TEAM_MEMBER,
                ConversationAccessRoleDTO.NON_TEAM_MEMBER,
                ConversationAccessRoleDTO.SERVICE,
                ConversationAccessRoleDTO.GUEST,
            ),
            access = setOf(
                ConversationAccessDTO.INVITE,
                ConversationAccessDTO.CODE,
                ConversationAccessDTO.PRIVATE,
                ConversationAccessDTO.LINK
            )
        )
        val newAccess = UpdateConversationAccessResponse.AccessUpdated(
            EventContentDTO.Conversation.AccessUpdate(
                conversationIdDTO,
                data = newAccessIndoDTO,
                qualifiedFrom = com.wire.kalium.network.api.base.model.UserId("from_id", "from_domain")
            )
        )

        val (arrange, conversationRepository) = Arrangement()
            .withApiUpdateAccessRoleReturns(NetworkResponse.Success(newAccess, mapOf(), 200))
            .withDaoUpdateAccessSuccess()
            .arrange()

        conversationRepository.updateAccessInfo(
            conversationID = ConversationId(conversationIdDTO.value, conversationIdDTO.domain),
            access = listOf(
                Conversation.Access.INVITE,
                Conversation.Access.CODE,
                Conversation.Access.PRIVATE,
                Conversation.Access.LINK
            ),
            accessRole = listOf(
                Conversation.AccessRole.TEAM_MEMBER,
                Conversation.AccessRole.NON_TEAM_MEMBER,
                Conversation.AccessRole.SERVICE,
                Conversation.AccessRole.GUEST
            )
        ).shouldSucceed()

        with(arrange) {
            verify(conversationApi)
                .coroutine { conversationApi.updateAccessRole(conversationIdDTO, newAccessIndoDTO) }
                .wasInvoked(exactly = once)

            verify(conversationDAO)
                .coroutine {
                    conversationDAO.updateAccess(
                        ConversationIDEntity(conversationIdDTO.value, conversationIdDTO.domain),
                        accessList = listOf(
                            ConversationEntity.Access.INVITE,
                            ConversationEntity.Access.CODE,
                            ConversationEntity.Access.PRIVATE,
                            ConversationEntity.Access.LINK
                        ),
                        accessRoleList = listOf(
                            ConversationEntity.AccessRole.TEAM_MEMBER,
                            ConversationEntity.AccessRole.NON_TEAM_MEMBER,
                            ConversationEntity.AccessRole.SERVICE,
                            ConversationEntity.AccessRole.GUEST
                        )
                    )
                }
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenUpdateConversationMemberRoleSuccess_whenUpdatingConversationMemberRole_thenTheNewRoleIsUpdatedLocally() = runTest {
        val (arrange, conversationRepository) = Arrangement()
            .withApiUpdateConversationMemberRoleReturns(NetworkResponse.Success(Unit, mapOf(), 200))
            .withDaoUpdateConversationMemberRoleSuccess()
            .arrange()
        val conversationId = ConversationId("conv_id", "conv_domain")
        val userId: UserId = UserId("user_id", "user_domain")
        val newRole = Conversation.Member.Role.Admin

        conversationRepository.updateConversationMemberRole(conversationId, userId, newRole).shouldSucceed()

        with(arrange) {
            verify(conversationApi)
                .coroutine {
                    conversationApi.updateConversationMemberRole(
                        MapperProvider.idMapper().toApiModel(conversationId),
                        MapperProvider.idMapper().toApiModel(userId),
                        ConversationMemberRoleDTO(MapperProvider.conversationRoleMapper().toApi(newRole))
                    )
                }
                .wasInvoked(exactly = once)

            verify(conversationDAO)
                .coroutine {
                    conversationDAO.updateConversationMemberRole(
                        MapperProvider.idMapper().toDaoModel(conversationId),
                        MapperProvider.idMapper().toDaoModel(userId),
                        MapperProvider.conversationRoleMapper().toDAO(newRole)
                    )
                }
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAConversation_WhenDeletingTheConversation_ThenShouldBeDeletedLocally() = runTest {
        val (arrange, conversationRepository) = Arrangement().withSuccessfulConversationDeletion().arrange()
        val conversationId = ConversationId("conv_id", "conv_domain")

        conversationRepository.deleteConversation(conversationId).shouldSucceed()

        with(arrange) {
            verify(conversationDAO)
                .suspendFunction(conversationDAO::deleteConversationByQualifiedID)
                .with(eq(MapperProvider.idMapper().toDaoModel(conversationId)))
                .wasInvoked(once)
        }
    }

    @Test
    fun givenAGroupConversationHasNewMessages_whenGettingConversationDetails_ThenCorrectlyGetUnreadMessageCount() = runTest {
        // given
        val conversationEntityFlow = flowOf(
            TestConversation.ENTITY.copy(
                type = ConversationEntity.Type.GROUP,
            )
        )

        given(conversationDAO)
            .suspendFunction(conversationDAO::observeGetConversationByQualifiedID)
            .whenInvokedWith(any())
            .thenReturn(conversationEntityFlow)

        given(timeParser)
            .function(timeParser::isTimeBefore)
            .whenInvokedWith(any(), any())
            .thenReturn(true)

        given(messageDAO)
            .suspendFunction(messageDAO::observeUnreadMessageCount)
            .whenInvokedWith(any())
            .thenReturn(flowOf(10))

        given(messageDAO)
            .suspendFunction(messageDAO::observeUnreadMentionsCount)
            .whenInvokedWith(any())
            .thenReturn(flowOf(10))

        given(messageDAO)
            .suspendFunction(messageDAO::observeLastUnreadMessage)
            .whenInvokedWith(any())
            .thenReturn(flowOf(TEST_MESSAGE_ENTITY))

        given(userRepository)
            .coroutine { userRepository.observeSelfUser() }
            .then { flowOf(TestUser.SELF) }
        // when
        conversationRepository.observeConversationDetailsById(TestConversation.ID).test {
            // then
            val conversationDetail = awaitItem()

            assertIs<Either.Right<ConversationDetails.Group>>(conversationDetail)
            assertTrue { conversationDetail.value.unreadMessagesCount == 10L }

            awaitComplete()
        }
    }

    @Test
    fun givenAGroupConversationHasNotNewMessages_whenGettingConversationDetails_ThenReturnZeroUnreadMessageCount() = runTest {
        // given
        val conversationEntityFlow = flowOf(
            TestConversation.ENTITY.copy(
                type = ConversationEntity.Type.GROUP,
            )
        )

        given(conversationDAO)
            .suspendFunction(conversationDAO::observeGetConversationByQualifiedID)
            .whenInvokedWith(any())
            .thenReturn(conversationEntityFlow)

        given(timeParser)
            .function(timeParser::isTimeBefore)
            .whenInvokedWith(any(), any())
            .thenReturn(false)

        given(messageDAO)
            .suspendFunction(messageDAO::observeUnreadMessageCount)
            .whenInvokedWith(any())
            .thenReturn(flowOf(0))

        given(messageDAO)
            .suspendFunction(messageDAO::observeUnreadMentionsCount)
            .whenInvokedWith(any())
            .thenReturn(flowOf(0))

        given(messageDAO)
            .suspendFunction(messageDAO::observeLastUnreadMessage)
            .whenInvokedWith(any())
            .thenReturn(flowOf(null))

        given(userRepository)
            .coroutine { userRepository.observeSelfUser() }
            .then { flowOf(TestUser.SELF) }
        // when
        conversationRepository.observeConversationDetailsById(TestConversation.ID).test {
            // then
            val conversationDetail = awaitItem()

            assertIs<Either.Right<ConversationDetails.Group>>(conversationDetail)
            assertTrue { conversationDetail.value.unreadMessagesCount == 0L }
            assertTrue { conversationDetail.value.lastUnreadMessage == null }

            awaitComplete()
        }
    }

    @Test
    fun givenAOneToOneConversationHasNotNewMessages_whenGettingConversationDetails_ThenReturnZeroUnreadMessageCount() = runTest {
        // given
        val conversationEntityFlow = flowOf(
            TestConversation.ENTITY.copy(
                type = ConversationEntity.Type.ONE_ON_ONE,
            )
        )

        given(conversationDAO)
            .suspendFunction(conversationDAO::observeGetConversationByQualifiedID)
            .whenInvokedWith(any())
            .thenReturn(conversationEntityFlow)

        given(timeParser)
            .function(timeParser::isTimeBefore)
            .whenInvokedWith(any(), any())
            .thenReturn(false)

        given(userRepository)
            .coroutine { userRepository.observeSelfUser() }
            .then { flowOf(TestUser.SELF) }

        given(conversationDAO)
            .suspendFunction(conversationDAO::getAllMembers)
            .whenInvokedWith(any())
            .thenReturn(flowOf(listOf(MemberEntity(TestUser.ENTITY_ID, MemberEntity.Role.Member))))

        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(flowOf(TestUser.OTHER))

        given(messageDAO)
            .suspendFunction(messageDAO::observeUnreadMessageCount)
            .whenInvokedWith(any())
            .thenReturn(flowOf(0))

        given(messageDAO)
            .suspendFunction(messageDAO::observeUnreadMentionsCount)
            .whenInvokedWith(any())
            .thenReturn(flowOf(0))

        given(messageDAO)
            .suspendFunction(messageDAO::observeLastUnreadMessage)
            .whenInvokedWith(any())
            .thenReturn(flowOf(null))

        // when
        conversationRepository.observeConversationDetailsById(TestConversation.ID).test {
            // then
            val conversationDetail = awaitItem()

            assertIs<Either.Right<ConversationDetails.OneOne>>(conversationDetail)
            assertTrue { conversationDetail.value.unreadMessagesCount == 0L }
            assertTrue { conversationDetail.value.lastUnreadMessage == null }

            awaitComplete()
        }
    }

    @Test
    fun givenAOneToOneConversationHasNewMessages_whenGettingConversationDetails_ThenCorrectlyGetUnreadMessageCount() = runTest {
        // given
        val conversationEntityFlow = flowOf(
            TestConversation.ENTITY.copy(
                type = ConversationEntity.Type.ONE_ON_ONE,
            )
        )

        given(conversationDAO)
            .suspendFunction(conversationDAO::observeGetConversationByQualifiedID)
            .whenInvokedWith(any())
            .thenReturn(conversationEntityFlow)

        given(timeParser)
            .function(timeParser::isTimeBefore)
            .whenInvokedWith(any(), any())
            .thenReturn(true)

        given(messageDAO)
            .suspendFunction(messageDAO::observeUnreadMessageCount)
            .whenInvokedWith(any())
            .thenReturn(flowOf(10))

        given(messageDAO)
            .suspendFunction(messageDAO::observeUnreadMentionsCount)
            .whenInvokedWith(any())
            .thenReturn(flowOf(10))

        given(userRepository)
            .coroutine { userRepository.observeSelfUser() }
            .then { flowOf(TestUser.SELF) }

        given(conversationDAO)
            .suspendFunction(conversationDAO::getAllMembers)
            .whenInvokedWith(any())
            .thenReturn(flowOf(listOf(MemberEntity(TestUser.ENTITY_ID, MemberEntity.Role.Member))))

        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(flowOf(TestUser.OTHER))

        given(messageDAO)
            .suspendFunction(messageDAO::observeLastUnreadMessage)
            .whenInvokedWith(any())
            .thenReturn(flowOf(TEST_MESSAGE_ENTITY))

        // when
        conversationRepository.observeConversationDetailsById(TestConversation.ID).test {
            // then
            val conversationDetail = awaitItem()

            assertIs<Either.Right<ConversationDetails.OneOne>>(conversationDetail)
            assertTrue { conversationDetail.value.unreadMessagesCount == 10L }
            assertTrue(conversationDetail.value.lastUnreadMessage != null)

            awaitComplete()
        }
    }

    @Test
    fun givenUserHasUnReadConversation_whenGettingUnReadConversationCount_ThenCorrectlyGetTheCount() = runTest {
        // given
        given(conversationDAO)
            .suspendFunction(conversationDAO::getUnreadConversationCount)
            .whenInvoked()
            .thenReturn(10L)

        // when
        val result = conversationRepository.getUnreadConversationCount()

        // then
        assertIs<Either.Right<Long>>(result)
        assertEquals(10L, result.value)
    }

    @Test
    fun givenAConversationDaoFailed_whenUpdatingTheConversationReadDate_thenShouldNotSucceed() = runTest {
        // given
        given(conversationDAO)
            .suspendFunction(conversationDAO::updateConversationReadDate)
            .whenInvokedWith(any(), any())
            .thenThrow(IllegalStateException("Some illegal state"))

        // when
        val result = conversationRepository.updateConversationReadDate(TestConversation.ID, "2022-03-30T15:36:00.000Z")

        // then
        verify(conversationDAO)
            .suspendFunction(conversationDAO::updateConversationReadDate)
            .with(anything(), anything())
            .wasInvoked()
        assertIs<Either.Left<StorageFailure>>(result)
    }

    @Test
    fun givenAMemberInAConversation_WhenCheckingIfItIsMember_ThenShouldSucceed() = runTest {
        val isMember = true

        // given
        val (arrangement, conversationRepository) = Arrangement()
            .withExpectedIsUserMemberFlow(flowOf(isMember))
            .arrange()

        // when
        conversationRepository.observeIsUserMember(CONVERSATION_ID, USER_ID).test {
            // then
            val isMemberResponse = awaitItem()

            assertIs<Either.Right<Boolean>>(isMemberResponse)
            assertEquals(isMemberResponse.value, isMember)

            verify(arrangement.conversationDAO)
                .suspendFunction(arrangement.conversationDAO::observeIsUserMember)
                .with(eq(CONVERSATION_ENTITY_ID), eq(USER_ENTITY_ID))
                .wasInvoked(exactly = once)

            awaitComplete()
        }

    }

    @Test
    fun givenAMemberIsNotInAConversation_WhenCheckingIfItIsMember_ThenShouldSucceed() = runTest {
        val isMember = false
        val (arrangement, conversationRepository) = Arrangement()
            .withExpectedIsUserMemberFlow(flowOf(isMember))
            .arrange()

        // when
        conversationRepository.observeIsUserMember(CONVERSATION_ID, USER_ID).test {
            // then
            val isMemberResponse = awaitItem()

            assertIs<Either.Right<Boolean>>(isMemberResponse)
            assertEquals(isMemberResponse.value, isMember)

            verify(arrangement.conversationDAO)
                .suspendFunction(arrangement.conversationDAO::observeIsUserMember)
                .with(eq(CONVERSATION_ENTITY_ID), eq(USER_ENTITY_ID))
                .wasInvoked(exactly = once)

            awaitComplete()
        }
    }

    @Test
    fun givenADeletedMember_WhenInvokingWhoDeletedMe_ThenDaoCallShouldSucceed() = runTest {
        val whoDeletedMe = UserId("deletion-author", "deletion-author-domain")
        val conversationId = ConversationId("conv_id", "conv_domain")
        val selfUserFlow = flowOf(TestUser.SELF)
        val (arrange, conversationRepository) = Arrangement().withSelfUserFlow(selfUserFlow).withWhoDeletedMe(whoDeletedMe).arrange()

        val result = conversationRepository.whoDeletedMe(conversationId)

        with(arrange) {
            result.shouldSucceed {}
            verify(conversationDAO)
                .suspendFunction(conversationDAO::whoDeletedMeInConversation)
                .with(any(), any())
                .wasInvoked(once)
        }
    }

    @Test
    fun givenAConversationId_WhenTheConversationDoesNotExists_ShouldReturnANullConversation() = runTest {
        val conversationId = ConversationId("conv_id", "conv_domain")
        val (_, conversationRepository) = Arrangement().withExpectedConversation().arrange()

        val result = conversationRepository.getConversationById(conversationId)
        assertNull(result)
    }

    @Test
    fun givenAConversationId_WhenTheConversationExists_ShouldReturnAConversationInstance() = runTest {
        val conversationId = ConversationId("conv_id", "conv_domain")
        val (_, conversationRepository) = Arrangement().withExpectedConversation(TestConversation.ENTITY).arrange()

        val result = conversationRepository.getConversationById(conversationId)
        assertNotNull(result)
    }

    @Test
    fun givenAConversation_WhenUpdatingTheName_ShouldReturnSuccess() = runTest {
        val conversationId = ConversationId("conv_id", "conv_domain")
        val (arrange, conversationRepository) = Arrangement().withExpectedConversation(TestConversation.ENTITY).arrange()

        val result = conversationRepository.updateConversationName(conversationId, "newName", "2022-03-30T15:36:00.000Z")
        with(result) {
            shouldSucceed()
            verify(arrange.conversationDAO)
                .suspendFunction(arrange.conversationDAO::updateConversationName)
                .with(any(), any(), any())
                .wasInvoked(exactly = once)
        }
    }

    private class Arrangement {
        @Mock
        val userRepository: UserRepository = mock(UserRepository::class)

        @Mock
        val mlsConversationRepository: MLSConversationRepository = mock(MLSConversationRepository::class)

        @Mock
        val conversationDAO: ConversationDAO = mock(ConversationDAO::class)

        @Mock
        val conversationApi: ConversationApi = mock(ConversationApi::class)

        @Mock
        val clientDao: ClientDAO = mock(ClientDAO::class)

        @Mock
        private val clientApi = mock(ClientApi::class)

        @Mock
        private val messageDAO = configure(mock(MessageDAO::class)) { stubsUnitByDefault = true }

        @Mock
        val timeParser: TimeParser = mock(TimeParser::class)

        val conversationRepository =
            ConversationDataSource(
                userRepository,
                mlsConversationRepository,
                conversationDAO,
                conversationApi,
                messageDAO,
                clientDao,
                clientApi,
                timeParser,
                TestUser.SELF.id
            )

        fun withSelfUserFlow(selfUserFlow: Flow<SelfUser>) = apply {
            given(userRepository)
                .suspendFunction(userRepository::observeSelfUser)
                .whenInvoked()
                .thenReturn(selfUserFlow)
        }

        fun withInsertConversations() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::insertConversations)
                .whenInvokedWith(anything())
                .thenReturn(Unit)
            given(conversationDAO)
                .suspendFunction(conversationDAO::insertMembersWithQualifiedId)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Unit)
        }

        fun withApiUpdateAccessRoleReturns(response: NetworkResponse<UpdateConversationAccessResponse>) = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::updateAccessRole)
                .whenInvokedWith(any(), any())
                .thenReturn(response)
        }

        fun withDaoUpdateAccessSuccess() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::updateAccess)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(Unit)
        }

        fun withApiUpdateConversationMemberRoleReturns(response: NetworkResponse<Unit>) = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::updateConversationMemberRole)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(response)
        }

        fun withDaoUpdateConversationMemberRoleSuccess() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::updateConversationMemberRole)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(Unit)
        }

        fun withConversationProtocolIs(protocolInfo: ConversationEntity.ProtocolInfo) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::getConversationByQualifiedID)
                .whenInvokedWith(any())
                .thenReturn(TestConversation.GROUP_ENTITY(protocolInfo))
        }

        fun withDeleteMemberAPISucceedChanged() = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::removeMember)
                .whenInvokedWith(any(), any())
                .thenReturn(
                    NetworkResponse.Success(
                        TestConversation.REMOVE_MEMBER_FROM_CONVERSATION_SUCCESSFUL_RESPONSE,
                        mapOf(),
                        HttpStatusCode.OK.value
                    )
                )
        }

        fun withDeleteMemberAPISucceedUnchanged() = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::removeMember)
                .whenInvokedWith(any(), any())
                .thenReturn(
                    NetworkResponse.Success(
                        ConversationMemberRemovedDTO.Unchanged,
                        mapOf(),
                        HttpStatusCode.OK.value
                    )
                )
        }

        fun withDeleteMemberAPIFailed() = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::removeMember)
                .whenInvokedWith(any(), any())
                .thenReturn(
                    NetworkResponse.Error(
                        KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))
                    )
                )

        }

        fun withSuccessfulMemberDeletion() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::deleteMemberByQualifiedID)
                .whenInvokedWith(any(), any())
                .thenReturn(Unit)
        }

        fun withSuccessfulLeaveMLSGroup() = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::leaveGroup)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withSuccessfulRemoveMemberFromMLSGroup() = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::removeMembersFromMLSGroup)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun withSuccessfulConversationDeletion() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::deleteConversationByQualifiedID)
                .whenInvokedWith(any())
                .thenReturn(Unit)
        }

        fun withExpectedIsUserMemberFlow(expectedIsUserMember: Flow<Boolean>) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::observeIsUserMember)
                .whenInvokedWith(any(), any())
                .thenReturn(expectedIsUserMember)
        }

        fun withExpectedConversation(conversationEntity: ConversationEntity? = null) = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::observeGetConversationByQualifiedID)
                .whenInvokedWith(any())
                .thenReturn(flowOf(conversationEntity))
        }

        fun withWhoDeletedMe(deletionAuthor: UserId?) = apply {
            val author = deletionAuthor?.let { MapperProvider.idMapper().toDaoModel(it) }
            given(conversationDAO)
                .suspendFunction(conversationDAO::whoDeletedMeInConversation)
                .whenInvokedWith(any(), any())
                .thenReturn(author)
        }

        fun arrange() = this to conversationRepository
    }

    companion object {
        private const val RAW_GROUP_ID = "mlsGroupId"
        val GROUP_ID = GroupID(RAW_GROUP_ID)
        val PROTEUS_PROTOCOL_INFO = ConversationEntity.ProtocolInfo.Proteus
        val MLS_PROTOCOL_INFO = ConversationEntity.ProtocolInfo
            .MLS(
                RAW_GROUP_ID,
                groupState = ConversationEntity.GroupState.ESTABLISHED,
                0UL,
                Instant.parse("2021-03-30T15:36:00.000Z"),
                cipherSuite = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            )

        const val GROUP_NAME = "Group Name"

        val CONVERSATION_ID = TestConversation.ID
        val USER_ID = TestUser.USER_ID

        val CONVERSATION_ENTITY_ID = QualifiedIDEntity(CONVERSATION_ID.value, CONVERSATION_ID.domain)
        val USER_ENTITY_ID = QualifiedIDEntity(USER_ID.value, USER_ID.domain)

        val CONVERSATION_IDS_DTO_ONE =
            ConversationIdDTO("someValue1", "someDomain1")

        val CONVERSATION_IDS_DTO_TWO =
            ConversationIdDTO("someValue2", "someDomain2")

        val CONVERSATION_RESPONSE = ConversationResponse(
            "creator",
            ConversationMembersResponse(
                ConversationMemberDTO.Self(MapperProvider.idMapper().toApiModel(TestUser.SELF.id), "wire_member"),
                emptyList()
            ),
            GROUP_NAME,
            TestConversation.NETWORK_ID,
            null,
            0UL,
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
            ),
            mlsCipherSuiteTag = null
        )

        val CONVERSATION_RESPONSE_DTO = ConversationResponseDTO(
            conversationsFound = listOf(CONVERSATION_RESPONSE),
            conversationsFailed = listOf(ConversationIdDTO("failedId", "someDomain")),
            conversationsNotFound = emptyList()

        )

        private val TEST_QUALIFIED_ID_ENTITY = PersistenceQualifiedId("value", "domain")

        val TEST_MESSAGE_ENTITY =
            MessageEntity.Regular(
                id = "uid",
                content = MessageEntityContent.Text("content"),
                conversationId = TEST_QUALIFIED_ID_ENTITY,
                date = "date",
                senderUserId = TEST_QUALIFIED_ID_ENTITY,
                senderClientId = "sender",
                status = MessageEntity.Status.SENT,
                editStatus = MessageEntity.EditStatus.NotEdited
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
            creatorId = "userTesValue",
            lastReadDate = "2022-03-30T15:36:00.000Z",
            lastModifiedDate = "2022-03-30T15:36:00.000Z",
            lastNotificationDate = null,
            access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
            accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER)
        )
    }
}
