package com.wire.kalium.logic.data.call

import app.cash.turbine.test
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.LegalHoldStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.TimeParserImpl
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.call.CallApi
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.call.CallDAO
import com.wire.kalium.persistence.dao.call.CallEntity
import io.ktor.util.reflect.instanceOf
import io.mockative.ConfigurationApi
import io.mockative.Mock
import io.mockative.Times
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.oneOf
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("LargeClass")
@OptIn(ConfigurationApi::class, ExperimentalCoroutinesApi::class)
class CallRepositoryTest {

    @Mock
    private val callApi = mock(classOf<CallApi>())

    @Mock
    private val conversationRepository = mock(classOf<ConversationRepository>())

    @Mock
    private val userRepository = mock(classOf<UserRepository>())

    @Mock
    private val teamRepository = mock(classOf<TeamRepository>())

    @Mock
    private val qualifiedIdMapper = mock(classOf<QualifiedIdMapper>())

    @Mock
    private val persistMessage = mock(classOf<PersistMessageUseCase>())

    @Mock
    private val callDAO = configure(mock(classOf<CallDAO>())) {
        stubsUnitByDefault = true
    }

    private lateinit var callRepository: CallRepository

    @BeforeTest
    fun setUp() {
        callRepository = CallDataSource(
            callApi = callApi,
            callDAO = callDAO,
            qualifiedIdMapper = qualifiedIdMapper,
            conversationRepository = conversationRepository,
            userRepository = userRepository,
            teamRepository = teamRepository,
            timeParser = TimeParserImpl(),
            persistMessage = persistMessage
        )
        given(qualifiedIdMapper).function(qualifiedIdMapper::fromStringToQualifiedID)
            .whenInvokedWith(eq("convId@domainId"))
            .thenReturn(QualifiedID("convId", "domainId"))

        given(qualifiedIdMapper).function(qualifiedIdMapper::fromStringToQualifiedID)
            .whenInvokedWith(eq("random@domain"))
            .thenReturn(QualifiedID("random", "domain"))

        given(qualifiedIdMapper).function(qualifiedIdMapper::fromStringToQualifiedID)
            .whenInvokedWith(eq("callerId@domain"))
            .thenReturn(QualifiedID("callerId", "domain"))

        given(qualifiedIdMapper).function(qualifiedIdMapper::fromStringToQualifiedID)
            .whenInvokedWith(eq("callerId"))
            .thenReturn(QualifiedID("callerId", ""))
    }

    @Test
    fun whenRequestingCallConfig_withNoLimitParam_ThenAResultIsReturned() = runTest {
        given(callApi)
            .suspendFunction(callApi::getCallConfig)
            .whenInvokedWith(oneOf(null))
            .thenReturn(NetworkResponse.Success(CALL_CONFIG_API_RESPONSE, mapOf(), 200))

        val result = callRepository.getCallConfigResponse(limit = null)

        result.shouldSucceed {
            assertEquals(CALL_CONFIG_API_RESPONSE, it)
        }
    }

    @Test
    fun givenEmptyListOfCalls_whenGetAllCallsIsCalled_thenReturnAnEmptyListOfCalls() = runTest {
        given(callDAO)
            .suspendFunction(callDAO::observeCalls)
            .whenInvoked()
            .thenReturn(flowOf(listOf()))

        val calls = callRepository.callsFlow()

        calls.test {
            assertEquals(0, awaitItem().size)
        }
    }

    @Test
    fun givenAListOfCallProfiles_whenGetAllCallsIsCalled_thenReturnAListOfCalls() = runTest {
        given(callDAO)
            .suspendFunction(callDAO::observeCalls)
            .whenInvoked()
            .thenReturn(
                flowOf(
                    listOf(
                        createCallEntity().copy(
                            status = CallEntity.Status.ESTABLISHED,
                            conversationType = ConversationEntity.Type.ONE_ON_ONE,
                            callerId = "callerId@domain"
                        )
                    )
                )
            )

        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    conversationId.toString() to createCallMetadata().copy(
                        isMuted = false,
                        conversationName = "ONE_ON_ONE Name",
                        conversationType = Conversation.Type.ONE_ON_ONE,
                        callerName = "otherUsername",
                        callerTeamName = "team_1"
                    )
                )
            )
        )

        val calls = callRepository.callsFlow()

        val expectedCall = provideCall(
            id = conversationId,
            status = CallStatus.ESTABLISHED
        )

        calls.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(expectedCall, list[0])
            assertTrue(list[0].instanceOf(Call::class))
        }
    }

    @Test
    fun whenStartingAGroupCall_withNoExistingCall_ThenSaveCallToDatabase() = runTest {
        // given
        val callEntity = createCallEntity().copy(
            status = CallEntity.Status.STARTED
        )

        given(conversationRepository).suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(any())
            .thenReturn(
                flowOf(
                    Either.Right(
                        ConversationDetails.Group(
                            groupConversation,
                            LegalHoldStatus.ENABLED,
                            false,
                            unreadMessagesCount = 0
                        )
                    )
                )
            )

        given(userRepository).suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(flowOf(TestUser.OTHER))

        given(userRepository).function(userRepository::getSelfUserId)
            .whenInvoked()
            .thenReturn(TestUser.USER_ID)

        given(teamRepository).suspendFunction(teamRepository::getTeam)
            .whenInvokedWith(any())
            .thenReturn(flowOf(Team("team1", "team_1")))

        given(callDAO)
            .suspendFunction(callDAO::getCallStatusByConversationId)
            .whenInvokedWith(eq(callEntity.conversationId))
            .thenReturn(null)

        given(callDAO)
            .suspendFunction(callDAO::insertCall)
            .whenInvokedWith(any())

        // when
        callRepository.createCall(
            conversationId = conversationId,
            status = CallStatus.STARTED,
            callerId = callerId.value,
            isMuted = true,
            isCameraOn = false
        )

        // then
        verify(callDAO).suspendFunction(callDAO::insertCall)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun whenStartingAGroupCall_withExistingClosedCall_ThenSaveCallToDatabase() = runTest {
        // given
        val callEntity = createCallEntity().copy(
            status = CallEntity.Status.STARTED
        )

        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    conversationId.toString() to createCallMetadata().copy(
                        isMuted = false
                    )
                )
            )
        )

        given(conversationRepository).suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(any())
            .thenReturn(
                flowOf(
                    Either.Right(
                        ConversationDetails.Group(
                            groupConversation,
                            LegalHoldStatus.ENABLED,
                            unreadMessagesCount = 0
                        )
                    )
                )
            )

        given(userRepository).suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(flowOf(TestUser.OTHER))

        given(userRepository).function(userRepository::getSelfUserId)
            .whenInvoked()
            .thenReturn(TestUser.USER_ID)

        given(teamRepository).suspendFunction(teamRepository::getTeam)
            .whenInvokedWith(any())
            .thenReturn(flowOf(Team("team1", "team_1")))

        given(callDAO)
            .suspendFunction(callDAO::getCallStatusByConversationId)
            .whenInvokedWith(eq(callEntity.conversationId))
            .thenReturn(CallEntity.Status.CLOSED)

        given(callDAO)
            .suspendFunction(callDAO::insertCall)
            .whenInvokedWith(any())

        // when
        callRepository.createCall(
            conversationId = conversationId,
            status = CallStatus.STARTED,
            callerId = callerId.value,
            isMuted = true,
            isCameraOn = false
        )

        // then
        verify(callDAO).suspendFunction(callDAO::insertCall)
            .with(any())
            .wasInvoked(exactly = once)

        assertEquals(
            true,
            callRepository.getCallMetadataProfile().data[conversationId.toString()]?.isMuted
        )
    }

    @Test
    fun whenIncomingGroupCall_withNonExistingCall_ThenSaveCallToDatabase() = runTest {
        // given
        val callEntity = createCallEntity().copy(
            status = CallEntity.Status.INCOMING
        )

        given(conversationRepository).suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(any())
            .thenReturn(
                flowOf(
                    Either.Right(
                        ConversationDetails.Group(
                            groupConversation,
                            LegalHoldStatus.ENABLED,
                            unreadMessagesCount = 0
                        )
                    )
                )
            )

        given(userRepository).suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(flowOf(TestUser.OTHER))

        given(userRepository).function(userRepository::getSelfUserId)
            .whenInvoked()
            .thenReturn(TestUser.USER_ID)

        given(teamRepository).suspendFunction(teamRepository::getTeam)
            .whenInvokedWith(any())
            .thenReturn(flowOf(Team("team1", "team_1")))

        given(callDAO)
            .suspendFunction(callDAO::getCallStatusByConversationId)
            .whenInvokedWith(eq(callEntity.conversationId))
            .thenReturn(null)

        given(callDAO)
            .suspendFunction(callDAO::insertCall)
            .whenInvokedWith(any())

        // when
        callRepository.createCall(
            conversationId = conversationId,
            status = CallStatus.INCOMING,
            callerId = callerId.value,
            isMuted = true,
            isCameraOn = false
        )

        // then
        verify(callDAO).suspendFunction(callDAO::insertCall)
            .with(any())
            .wasInvoked(exactly = once)

        assertTrue(
            callRepository.getCallMetadataProfile().data.containsKey(conversationId.toString())
        )
    }

    @Test
    fun whenIncomingGroupCall_withExistingCallMetadata_ThenDontSaveCallToDatabase() = runTest {
        // given
        val callEntity = createCallEntity().copy(
            status = CallEntity.Status.INCOMING
        )

        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    conversationId.toString() to createCallMetadata().copy(
                        isMuted = false
                    )
                )
            )
        )

        given(conversationRepository).suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(any())
            .thenReturn(
                flowOf(
                    Either.Right(
                        ConversationDetails.Group(
                            groupConversation,
                            LegalHoldStatus.ENABLED,
                            unreadMessagesCount = 0
                        )
                    )
                )
            )

        given(userRepository).suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(flowOf(TestUser.OTHER))

        given(userRepository).function(userRepository::getSelfUserId)
            .whenInvoked()
            .thenReturn(TestUser.USER_ID)

        given(teamRepository).suspendFunction(teamRepository::getTeam)
            .whenInvokedWith(any())
            .thenReturn(flowOf(Team("team1", "team_1")))

        given(callDAO)
            .suspendFunction(callDAO::getCallStatusByConversationId)
            .whenInvokedWith(eq(callEntity.conversationId))
            .thenReturn(CallEntity.Status.ESTABLISHED)

        // when
        callRepository.createCall(
            conversationId = conversationId,
            status = CallStatus.INCOMING,
            callerId = callerId.value,
            isMuted = true,
            isCameraOn = false
        )

        // then
        verify(callDAO).suspendFunction(callDAO::insertCall)
            .with(any())
            .wasInvoked(exactly = Times(0))

        assertTrue(
            callRepository.getCallMetadataProfile().data.containsKey(conversationId.toString())
        )
    }

    @Test
    fun whenIncomingGroupCall_withNonExistingCallMetadata_ThenUpdateCallInDatabase() = runTest {
        // given
        val callEntity = createCallEntity().copy(
            status = CallEntity.Status.INCOMING
        )

        given(conversationRepository).suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(any())
            .thenReturn(
                flowOf(
                    Either.Right(
                        ConversationDetails.Group(
                            groupConversation,
                            LegalHoldStatus.ENABLED,
                            unreadMessagesCount = 0
                        )
                    )
                )
            )

        given(userRepository).suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(flowOf(TestUser.OTHER))

        given(userRepository).function(userRepository::getSelfUserId)
            .whenInvoked()
            .thenReturn(TestUser.USER_ID)

        given(teamRepository).suspendFunction(teamRepository::getTeam)
            .whenInvokedWith(any())
            .thenReturn(flowOf(Team("team1", "team_1")))

        given(callDAO)
            .suspendFunction(callDAO::getCallStatusByConversationId)
            .whenInvokedWith(eq(callEntity.conversationId))
            .thenReturn(CallEntity.Status.ESTABLISHED)

        // when
        callRepository.createCall(
            conversationId = conversationId,
            status = CallStatus.INCOMING,
            callerId = callerId.value,
            isMuted = true,
            isCameraOn = false
        )

        // then
        verify(callDAO).suspendFunction(callDAO::updateLastCallStatusByConversationId)
            .with(
                eq(CallEntity.Status.STILL_ONGOING),
                eq(callEntity.conversationId)
            )
            .wasInvoked(exactly = once)

        assertTrue(
            callRepository.getCallMetadataProfile().data.containsKey(conversationId.toString())
        )
    }

    @Test
    fun whenStartingAOneOnOneCall_withNoExistingCall_ThenSaveCallToDatabase() = runTest {
        // given
        val callEntity = createCallEntity().copy(
            status = CallEntity.Status.STARTED,
            conversationType = ConversationEntity.Type.ONE_ON_ONE
        )

        given(conversationRepository).suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(any())
            .thenReturn(flowOf(Either.Right(oneOnOneConversationDetails)))

        given(userRepository).suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(flowOf(TestUser.OTHER))

        given(userRepository).function(userRepository::getSelfUserId)
            .whenInvoked()
            .thenReturn(TestUser.USER_ID)

        given(teamRepository).suspendFunction(teamRepository::getTeam)
            .whenInvokedWith(any())
            .thenReturn(flowOf(Team("team1", "team_1")))

        given(callDAO)
            .suspendFunction(callDAO::getCallStatusByConversationId)
            .whenInvokedWith(eq(callEntity.conversationId))
            .thenReturn(null)

        given(callDAO)
            .suspendFunction(callDAO::insertCall)
            .whenInvokedWith(any())

        // when
        callRepository.createCall(
            conversationId = conversationId,
            status = CallStatus.STARTED,
            callerId = callerId.value,
            isMuted = true,
            isCameraOn = false
        )

        // then
        verify(callDAO).suspendFunction(callDAO::insertCall)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun whenStartingAOneOnOneCall_withExistingClosedCall_ThenSaveCallToDatabase() = runTest {
        // given
        val callEntity = createCallEntity().copy(
            status = CallEntity.Status.STARTED,
            conversationType = ConversationEntity.Type.ONE_ON_ONE
        )

        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    conversationId.toString() to createCallMetadata().copy(
                        isMuted = false
                    )
                )
            )
        )

        given(conversationRepository).suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(any())
            .thenReturn(flowOf(Either.Right(oneOnOneConversationDetails)))

        given(userRepository).suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(flowOf(TestUser.OTHER))

        given(userRepository).function(userRepository::getSelfUserId)
            .whenInvoked()
            .thenReturn(TestUser.USER_ID)

        given(teamRepository).suspendFunction(teamRepository::getTeam)
            .whenInvokedWith(any())
            .thenReturn(flowOf(Team("team1", "team_1")))

        given(persistMessage).suspendFunction(persistMessage::invoke)
            .whenInvokedWith(any())
            .thenReturn(Either.Right(Unit))

        given(callDAO)
            .suspendFunction(callDAO::getCallStatusByConversationId)
            .whenInvokedWith(eq(callEntity.conversationId))
            .thenReturn(CallEntity.Status.CLOSED)

        given(callDAO)
            .suspendFunction(callDAO::insertCall)
            .whenInvokedWith(any())

        // when
        callRepository.createCall(
            conversationId = conversationId,
            status = CallStatus.STARTED,
            callerId = callerId.value,
            isMuted = true,
            isCameraOn = false
        )

        // then
        verify(callDAO).suspendFunction(callDAO::insertCall)
            .with(any())
            .wasInvoked(exactly = once)

        verify(persistMessage)
            .suspendFunction(persistMessage::invoke)
            .with(any())
            .wasNotInvoked()

        assertEquals(
            true,
            callRepository.getCallMetadataProfile().data[conversationId.toString()]?.isMuted
        )
    }

    @Test
    fun whenIncomingOneOnOneCall_withNonExistingCall_ThenSaveCallToDatabase() = runTest {
        // given
        val callEntity = createCallEntity().copy(
            status = CallEntity.Status.INCOMING,
            conversationType = ConversationEntity.Type.ONE_ON_ONE
        )

        given(conversationRepository).suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(any())
            .thenReturn(flowOf(Either.Right(oneOnOneConversationDetails)))

        given(userRepository).suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(flowOf(TestUser.OTHER))

        given(userRepository).function(userRepository::getSelfUserId)
            .whenInvoked()
            .thenReturn(TestUser.USER_ID)

        given(teamRepository).suspendFunction(teamRepository::getTeam)
            .whenInvokedWith(any())
            .thenReturn(flowOf(Team("team1", "team_1")))

        given(callDAO)
            .suspendFunction(callDAO::getCallStatusByConversationId)
            .whenInvokedWith(eq(callEntity.conversationId))
            .thenReturn(null)

        given(callDAO)
            .suspendFunction(callDAO::insertCall)
            .whenInvokedWith(any())

        // when
        callRepository.createCall(
            conversationId = conversationId,
            status = CallStatus.INCOMING,
            callerId = callerId.value,
            isMuted = true,
            isCameraOn = false
        )

        verify(persistMessage)
            .suspendFunction(persistMessage::invoke)
            .with(any())
            .wasInvoked(exactly = Times(0))

        verify(callDAO).suspendFunction(callDAO::insertCall)
            .with(any())
            .wasInvoked(exactly = once)

        assertTrue(
            callRepository.getCallMetadataProfile().data.containsKey(conversationId.toString())
        )
    }

    @Test
    fun whenIncomingOneOnOneCall_withExistingCallMetadata_ThenDontSaveCallToDatabase() = runTest {
        // given
        val callEntity = createCallEntity().copy(
            status = CallEntity.Status.INCOMING,
            conversationType = ConversationEntity.Type.ONE_ON_ONE
        )

        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    conversationId.toString() to createCallMetadata().copy(
                        isMuted = false
                    )
                )
            )
        )

        given(conversationRepository).suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(any())
            .thenReturn(flowOf(Either.Right(oneOnOneConversationDetails)))

        given(userRepository).suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(flowOf(TestUser.OTHER))

        given(userRepository).function(userRepository::getSelfUserId)
            .whenInvoked()
            .thenReturn(TestUser.USER_ID)

        given(teamRepository).suspendFunction(teamRepository::getTeam)
            .whenInvokedWith(any())
            .thenReturn(flowOf(Team("team1", "team_1")))

        given(persistMessage).suspendFunction(persistMessage::invoke)
            .whenInvokedWith(any())
            .thenReturn(Either.Right(Unit))

        given(callDAO)
            .suspendFunction(callDAO::getCallStatusByConversationId)
            .whenInvokedWith(eq(callEntity.conversationId))
            .thenReturn(CallEntity.Status.ESTABLISHED)

        // when
        callRepository.createCall(
            conversationId = conversationId,
            status = CallStatus.INCOMING,
            callerId = callerId.value,
            isMuted = true,
            isCameraOn = false
        )

        // then
        verify(callDAO).suspendFunction(callDAO::insertCall)
            .with(any())
            .wasInvoked(exactly = Times(0))

        verify(persistMessage)
            .suspendFunction(persistMessage::invoke)
            .with(any())
            .wasNotInvoked()

        assertTrue(
            callRepository.getCallMetadataProfile().data.containsKey(conversationId.toString())
        )
    }

    @Test
    fun whenIncomingOneOnOneCall_withNonExistingCallMetadata_ThenUpdateCallMetadata() = runTest {
        // given
        val callEntity = createCallEntity().copy(
            status = CallEntity.Status.INCOMING,
            conversationType = ConversationEntity.Type.ONE_ON_ONE
        )

        given(conversationRepository).suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(any())
            .thenReturn(flowOf(Either.Right(oneOnOneConversationDetails)))

        given(userRepository).suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(flowOf(TestUser.OTHER))

        given(userRepository).function(userRepository::getSelfUserId)
            .whenInvoked()
            .thenReturn(TestUser.USER_ID)

        given(teamRepository).suspendFunction(teamRepository::getTeam)
            .whenInvokedWith(any())
            .thenReturn(flowOf(Team("team1", "team_1")))

        given(callDAO)
            .suspendFunction(callDAO::getCallStatusByConversationId)
            .whenInvokedWith(eq(callEntity.conversationId))
            .thenReturn(CallEntity.Status.ESTABLISHED)

        given(callDAO)
            .suspendFunction(callDAO::getCallerIdByConversationId)
            .whenInvokedWith(any())
            .thenReturn("callerId@domain")

        given(persistMessage)
            .suspendFunction(persistMessage::invoke)
            .whenInvokedWith(any())
            .thenReturn(Either.Right(Unit))

        // when
        callRepository.createCall(
            conversationId = conversationId,
            status = CallStatus.INCOMING,
            callerId = callerId.value,
            isMuted = true,
            isCameraOn = false
        )

        // then
        verify(callDAO).suspendFunction(callDAO::updateLastCallStatusByConversationId)
            .with(eq(CallEntity.Status.CLOSED), eq(callEntity.conversationId))
            .wasInvoked(exactly = once)

        verify(callDAO).suspendFunction(callDAO::insertCall)
            .with(any())
            .wasInvoked(exactly = once)

        verify(persistMessage)
            .suspendFunction(persistMessage::invoke)
            .with(any())
            .wasInvoked(exactly = once)

        assertTrue(
            callRepository.getCallMetadataProfile().data.containsKey(conversationId.toString())
        )
    }

    @Test
    fun givenAConversationIdThatExistsInTheFlow_whenUpdateCallStatus_thenUpdateCallStatusIsCalledCorrectly() = runTest {
        // given
        val callEntity = createCallEntity()

        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    conversationId.toString() to createCallMetadata().copy(
                        isMuted = false
                    )
                )
            )
        )

        // when
        callRepository.updateCallStatusById(conversationId.toString(), CallStatus.ESTABLISHED)

        // then
        verify(callDAO)
            .suspendFunction(callDAO::updateLastCallStatusByConversationId)
            .with(
                eq(CallEntity.Status.ESTABLISHED),
                eq(callEntity.conversationId)
            )
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationIdThatDoesNotExistsInTheFlow_whenUpdateCallStatusIsCalled_thenUpdateTheStatus() = runTest {
        callRepository.updateCallStatusById(randomConversationIdString, CallStatus.INCOMING)

        verify(callDAO)
            .suspendFunction(callDAO::updateLastCallStatusByConversationId)
            .with(any(), any())
            .wasInvoked(exactly = Times(1))
    }

    @Test
    fun givenAConversationIdThatDoesNotExistsInTheFlow_whenUpdateIsMutedByIdIsCalled_thenDoNotUpdateTheFlow() = runTest {
        callRepository.updateIsMutedById(randomConversationIdString, false)

        assertFalse {
            callRepository.getCallMetadataProfile().data.containsKey(randomConversationIdString)
        }
    }

    @Test
    fun givenAConversationIdThatExistsInTheFlow_whenUpdateIsMutedByIdIsCalled_thenUpdateCallStatusInTheFlow() = runTest {
        // given
        val expectedValue = false
        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    conversationId.toString() to createCallMetadata().copy(
                        isMuted = true
                    )
                )
            )
        )

        // when
        callRepository.updateIsMutedById(conversationId.toString(), expectedValue)

        // then
        assertEquals(
            expectedValue,
            callRepository.getCallMetadataProfile().data[conversationId.toString()]?.isMuted
        )
    }

    @Test
    fun givenAConversationIdThatDoesNotExistsInTheFlow_whenUpdateIsCameraOnByIdIsCalled_thenDoNotUpdateTheFlow() = runTest {
        callRepository.updateIsCameraOnById(randomConversationIdString, false)

        assertFalse {
            callRepository.getCallMetadataProfile().data.containsKey(randomConversationIdString)
        }
    }

    @Test
    fun givenAConversationIdThatExistsInTheFlow_whenUpdateIsCameraOnByIdIsCalled_thenUpdateCallStatusInTheFlow() = runTest {
        // given
        val expectedValue = false
        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    conversationId.toString() to createCallMetadata().copy(
                        isCameraOn = true
                    )
                )
            )
        )

        // when
        callRepository.updateIsCameraOnById(conversationId.toString(), expectedValue)

        // then
        assertEquals(
            expectedValue,
            callRepository.getCallMetadataProfile().data[conversationId.toString()]?.isCameraOn
        )
    }

    @Test
    fun givenAConversationIdThatDoesNotExistsInTheFlow_whenUpdateCallParticipantsIsCalled_thenDoNotUpdateTheFlow() = runTest {
        callRepository.updateCallParticipants(randomConversationIdString, emptyList())

        assertFalse {
            callRepository.getCallMetadataProfile().data.containsKey(randomConversationIdString)
        }
    }

    @Test
    fun givenAConversationIdThatExistsInTheFlow_whenUpdateCallParticipantsIsCalled_thenUpdateCallStatusInTheFlow() = runTest {
        // given
        val participantsList = listOf(
            Participant(
                id = QualifiedID("participantId", "participantDomain"),
                clientId = "abcd",
                name = "name",
                isMuted = true,
                isSpeaking = false,
                avatarAssetId = null
            )
        )
        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    conversationId.toString() to createCallMetadata().copy(
                        participants = emptyList(),
                        maxParticipants = 0
                    )
                )
            )
        )

        // when
        callRepository.updateCallParticipants(conversationId.toString(), participantsList)

        // then
        val metadata = callRepository.getCallMetadataProfile().data[conversationId.toString()]
        assertEquals(
            participantsList,
            metadata?.participants
        )
    }

    @Test
    fun givenAConversationIdThatDoesNotExistsInTheFlow_whenUpdateParticipantsActiveSpeakerIsCalled_thenDoNotUpdateTheFlow() = runTest {
        callRepository.updateParticipantsActiveSpeaker(randomConversationIdString, CallActiveSpeakers(emptyList()))

        assertFalse {
            callRepository.getCallMetadataProfile().data.containsKey(randomConversationIdString)
        }
    }

    @Test
    fun givenAConversationIdThatExistsInTheFlow_whenUpdateParticipantActiveSpeakerIsCalled_thenUpdateCallStatusInTheFlow() = runTest {
        // given
        val participant = Participant(
            id = QualifiedID("participantId", "participantDomain"),
            clientId = "abcd",
            name = "name",
            isMuted = true,
            isSpeaking = false,
            avatarAssetId = null
        )
        val participantsList = listOf(participant)
        val expectedParticipantsList = listOf(participant.copy(isSpeaking = true))
        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    conversationId.toString() to createCallMetadata().copy(
                        participants = emptyList(),
                        maxParticipants = 0
                    )
                )
            )
        )
        val activeSpeakers = CallActiveSpeakers(
            activeSpeakers = listOf(
                CallActiveSpeaker(
                    userId = "participantId@participantDomain",
                    clientId = "abcd",
                    audioLevel = 1,
                    audioLevelNow = 1
                )
            )
        )

        callRepository.updateCallParticipants(conversationId.toString(), participantsList)

        // when
        callRepository.updateParticipantsActiveSpeaker(conversationId.toString(), activeSpeakers)

        // then
        val metadata = callRepository.getCallMetadataProfile().data[conversationId.toString()]
        assertEquals(
            expectedParticipantsList,
            metadata?.participants
        )

        assertEquals(
            true,
            metadata?.participants?.get(0)?.isSpeaking
        )
    }

    @Test
    fun givenAnIncomingCall_whenRequestingIncomingCalls_thenReturnTheIncomingCall() = runTest {
        // given
        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    conversationId.toString() to createCallMetadata().copy(
                        isMuted = false,
                        conversationName = "ONE_ON_ONE Name",
                        conversationType = Conversation.Type.ONE_ON_ONE,
                        callerName = "otherUsername",
                        callerTeamName = "team_1"
                    )
                )
            )
        )

        val callEntity = createCallEntity().copy(
            status = CallEntity.Status.INCOMING,
            callerId = "callerId@domain",
            conversationType = ConversationEntity.Type.ONE_ON_ONE
        )

        val expectedCall = provideCall(
            id = conversationId,
            status = CallStatus.INCOMING
        )

        given(callDAO)
            .suspendFunction(callDAO::observeIncomingCalls)
            .whenInvoked()
            .thenReturn(flowOf(listOf(callEntity)))

        // when
        val incomingCalls = callRepository.incomingCallsFlow()

        // then
        incomingCalls.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(
                expectedCall,
                list[0]
            )
        }
    }

    @Test
    fun givenAnOngoingCall_whenRequestingOngoingCalls_thenReturnTheOngoingCall() = runTest {
        // given
        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    conversationId.toString() to createCallMetadata().copy(
                        isMuted = false,
                        conversationName = "ONE_ON_ONE Name",
                        conversationType = Conversation.Type.ONE_ON_ONE,
                        callerName = "otherUsername",
                        callerTeamName = "team_1"
                    )
                )
            )
        )

        val callEntity = createCallEntity().copy(
            status = CallEntity.Status.STILL_ONGOING,
            callerId = "callerId@domain",
            conversationType = ConversationEntity.Type.ONE_ON_ONE
        )

        val expectedCall = provideCall(
            id = conversationId,
            status = CallStatus.STILL_ONGOING
        )

        given(callDAO)
            .suspendFunction(callDAO::observeOngoingCalls)
            .whenInvoked()
            .thenReturn(flowOf(listOf(callEntity)))

        // when
        val ongoingCalls = callRepository.ongoingCallsFlow()

        // then
        ongoingCalls.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(
                expectedCall,
                list[0]
            )
        }
    }

    @Test
    fun givenAnEstablishedCall_whenRequestingEstablishedCalls_thenReturnTheEstablishedCall() = runTest {
        // given
        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    conversationId.toString() to createCallMetadata().copy(
                        isMuted = false,
                        conversationName = "ONE_ON_ONE Name",
                        conversationType = Conversation.Type.ONE_ON_ONE,
                        callerName = "otherUsername",
                        callerTeamName = "team_1"
                    )
                )
            )
        )

        val callEntity = createCallEntity().copy(
            status = CallEntity.Status.ESTABLISHED,
            callerId = "callerId@domain",
            conversationType = ConversationEntity.Type.ONE_ON_ONE
        )

        val expectedCall = provideCall(
            id = conversationId,
            status = CallStatus.ESTABLISHED
        )

        given(callDAO)
            .suspendFunction(callDAO::observeEstablishedCalls)
            .whenInvoked()
            .thenReturn(flowOf(listOf(callEntity)))

        // when
        val establishedCalls = callRepository.establishedCallsFlow()

        // then
        establishedCalls.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(
                expectedCall,
                list[0]
            )
        }
    }

    @Suppress("LongMethod")
    @Test
    fun givenSomeCalls_whenRequestingCalls_thenReturnTheCalls() = runTest {
        // given
        val metadata = createCallMetadata().copy(
            isMuted = false,
            conversationName = "ONE_ON_ONE Name",
            conversationType = Conversation.Type.ONE_ON_ONE,
            callerName = "otherUsername",
            callerTeamName = "team_1"
        )
        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    conversationId.toString() to metadata,
                    randomConversationId.toString() to metadata.copy(
                        conversationName = "CLOSED CALL"
                    )
                )
            )
        )

        val missedCall = createCallEntity().copy(
            status = CallEntity.Status.MISSED,
            callerId = "callerId@domain",
            conversationType = ConversationEntity.Type.ONE_ON_ONE
        )

        val closedCall = createCallEntity().copy(
            conversationId = QualifiedIDEntity(
                value = randomConversationId.value,
                domain = randomConversationId.domain
            ),
            status = CallEntity.Status.CLOSED,
            callerId = "callerId@domain",
            conversationType = ConversationEntity.Type.ONE_ON_ONE
        )

        val expectedMissedCall = provideCall(
            id = conversationId,
            status = CallStatus.MISSED
        )

        val expectedClosedCall = provideCall(
            id = randomConversationId,
            status = CallStatus.CLOSED
        ).copy(
            conversationName = "CLOSED CALL"
        )

        given(callDAO)
            .suspendFunction(callDAO::observeCalls)
            .whenInvoked()
            .thenReturn(flowOf(listOf(missedCall, closedCall)))

        // when
        val establishedCalls = callRepository.callsFlow()

        // then
        establishedCalls.test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertEquals(
                expectedMissedCall,
                list[0]
            )
            assertEquals(
                expectedClosedCall,
                list[1]
            )
        }
    }

    private fun provideCall(id: ConversationId, status: CallStatus) = Call(
        conversationId = id,
        status = status,
        callerId = "callerId@domain",
        participants = listOf(),
        isMuted = false,
        isCameraOn = false,
        maxParticipants = 0,
        conversationName = "ONE_ON_ONE Name",
        conversationType = Conversation.Type.ONE_ON_ONE,
        callerName = "otherUsername",
        callerTeamName = "team_1"
    )

    private fun createCallEntity() = CallEntity(
        conversationId = QualifiedIDEntity(
            value = conversationId.value,
            domain = conversationId.domain
        ),
        id = "abcd-1234",
        status = CallEntity.Status.STARTED,
        callerId = callerId.toString(),
        conversationType = ConversationEntity.Type.GROUP
    )

    private fun createCallMetadata() = CallMetadata(
        isMuted = true,
        isCameraOn = false,
        conversationName = null,
        conversationType = Conversation.Type.GROUP,
        callerName = null,
        callerTeamName = null
    )

    private companion object {
        const val CALL_CONFIG_API_RESPONSE = "{'call':'success','config':'dummy_config'}"
        private const val randomConversationIdString = "random@domain"
        private val randomConversationId = ConversationId("value", "domain")

        private val conversationId = ConversationId(value = "convId", domain = "domainId")
        private val groupConversation = TestConversation.GROUP().copy(id = conversationId)
        private val oneOnOneConversation = TestConversation.one_on_one(conversationId)
        private val callerId = UserId(value = "callerId", domain = "domain")

        private val oneOnOneConversationDetails = ConversationDetails.OneOne(
            conversation = oneOnOneConversation,
            otherUser = TestUser.OTHER,
            connectionState = ConnectionState.ACCEPTED,
            legalHoldStatus = LegalHoldStatus.ENABLED,
            userType = UserType.INTERNAL,
            unreadMessagesCount = 0
        )
    }
}
