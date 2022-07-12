package com.wire.kalium.logic.data.call

import app.cash.turbine.test
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.LegalHoldStatus
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.TimeParserImpl
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.call.CallApi
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.util.reflect.instanceOf
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.oneOf
import io.mockative.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    private val persistMessage = mock(classOf<PersistMessageUseCase>())

    private lateinit var mapOfCallProfiles: Map<String, Call>
    private lateinit var startedCall: Call
    private lateinit var answeredCall: Call
    private lateinit var incomingCall: Call
    private lateinit var establishedCall: Call

    private lateinit var callRepository: CallRepository

    @BeforeTest
    fun setUp() {
        callRepository = CallDataSource(
            callApi = callApi,
            conversationRepository = conversationRepository,
            userRepository = userRepository,
            teamRepository = teamRepository,
            timeParser = TimeParserImpl(),
            persistMessage = persistMessage
        )
        startedCall = provideCall(sharedConversationId, CallStatus.STARTED)
        answeredCall = provideCall(conversationIdAnsweredCall, CallStatus.ANSWERED)
        incomingCall = provideCall(conversationIdIncomingCall, CallStatus.INCOMING)
        establishedCall = provideCall(conversationIdEstablishedCall, CallStatus.ESTABLISHED)

        mapOfCallProfiles = mapOf(
            startedCall.conversationId.toString() to startedCall,
            incomingCall.conversationId.toString() to incomingCall,
            establishedCall.conversationId.toString() to establishedCall,
            answeredCall.conversationId.toString() to answeredCall
        )
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
        val calls = callRepository.callsFlow()

        calls.test {
            assertEquals(0, awaitItem().size)
        }
    }

    @Test
    fun givenAListOfCallProfiles_whenGetAllCallsIsCalled_thenReturnAListOfCalls() = runTest {
        callRepository.updateCallProfileFlow(CallProfile(mapOfCallProfiles))

        val calls = callRepository.callsFlow()

        calls.test {
            val list = awaitItem()
            assertEquals(mapOfCallProfiles.size, list.size)
            assertTrue(list[0].instanceOf(Call::class))
        }
    }

    @Test
    fun givenACallData_whenCreateCallCalled_thenAddThatCallToTheFlow() = runTest {
        given(conversationRepository).suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(any())
            .thenReturn(flowOf(ConversationDetails.Group(TestConversation.ONE_ON_ONE, LegalHoldStatus.ENABLED)))
        given(userRepository).suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(flowOf(TestUser.OTHER))
        given(userRepository).suspendFunction(userRepository::getSelfUserId)
            .whenInvoked()
            .thenReturn(TestUser.USER_ID)
        given(teamRepository).suspendFunction(teamRepository::getTeam)
            .whenInvokedWith(any())
            .thenReturn(flowOf(Team("team1", "team_1")))
        callRepository.updateCallProfileFlow(CallProfile(mapOf(startedCall.conversationId.toString() to startedCall)))

        callRepository.createCall(conversationIdAnsweredCall, CallStatus.ANSWERED, "caller_id", false, false)

        val calls = callRepository.callsFlow()

        calls.test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertEquals(list[0], startedCall)
            assertEquals(list[1], answeredCall)

            verify(conversationRepository)
                .suspendFunction(conversationRepository::observeConversationDetailsById)
                .with(any())
                .wasInvoked(exactly = once)

            verify(userRepository)
                .suspendFunction(userRepository::getKnownUser)
                .with(any())
                .wasInvoked(exactly = once)

            verify(teamRepository)
                .suspendFunction(teamRepository::getTeam)
                .with(any())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenACallDataWithSameConversationIdAsAnotherOneInTheFlow_whenCreateCallCalled_thenReplaceTheCurrent() = runTest {
        given(conversationRepository).suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(any())
            .thenReturn(flowOf(ConversationDetails.Group(TestConversation.ONE_ON_ONE, LegalHoldStatus.ENABLED)))
        given(userRepository).suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(flowOf(TestUser.OTHER))
        given(userRepository).suspendFunction(userRepository::getSelfUserId)
            .whenInvoked()
            .thenReturn(TestUser.USER_ID)
        given(teamRepository).suspendFunction(teamRepository::getTeam)
            .whenInvokedWith(any())
            .thenReturn(flowOf(Team("team1", "team_1")))

        val incomingCall2 = provideCall(sharedConversationId, CallStatus.INCOMING)
        callRepository.updateCallProfileFlow(CallProfile(mapOfCallProfiles))

        callRepository.createCall(sharedConversationId, CallStatus.INCOMING, "caller_id", false, false)

        val calls = callRepository.callsFlow()
        calls.test {
            val list = awaitItem()
            assertEquals(mapOfCallProfiles.size, list.size)
            assertEquals(incomingCall2, list[0])

            verify(conversationRepository)
                .suspendFunction(conversationRepository::observeConversationDetailsById)
                .with(any())
                .wasInvoked(exactly = once)

            verify(userRepository)
                .suspendFunction(userRepository::getKnownUser)
                .with(any())
                .wasInvoked(exactly = once)

            verify(teamRepository)
                .suspendFunction(teamRepository::getTeam)
                .with(any())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAConversationIdThatDoesNotExistsInTheFlow_whenUpdateCallStatusIsCalled_thenDoNotUpdateTheFlow() = runTest {
        callRepository.updateCallStatusById(randomConversationIdString, CallStatus.INCOMING)

        val calls = callRepository.callsFlow()
        calls.test {
            val list = awaitItem()
            assertEquals(0, list.size)
        }
    }

    @Test
    fun givenAConversationIdThatExistsInTheFlow_whenUpdateCallStatusIsCalled_thenUpdateCallStatusInTheFlow() = runTest {
        callRepository.updateCallProfileFlow(CallProfile(mapOfCallProfiles))

        callRepository.updateCallStatusById(startedCall.conversationId.toString(), CallStatus.ESTABLISHED)

        val calls = callRepository.callsFlow()
        calls.test {
            val list = awaitItem()
            assertEquals(mapOfCallProfiles.size, list.size)
            assertEquals(list[0].status, CallStatus.ESTABLISHED)
        }
    }


    @Test
    fun givenAConversationIdThatDoesNotExistsInTheFlow_whenUpdateIsMutedByIdIsCalled_thenDoNotUpdateTheFlow() = runTest {
        callRepository.updateIsMutedById(randomConversationIdString, false)

        val calls = callRepository.callsFlow()
        assertEquals(0, calls.first().size)
    }

    @Test
    fun givenAConversationIdThatExistsInTheFlow_whenUpdateIsMutedByIdIsCalled_thenUpdateCallStatusInTheFlow() = runTest {
        val expectedValue = true
        callRepository.updateCallProfileFlow(CallProfile(mapOfCallProfiles))

        callRepository.updateIsMutedById(startedCall.conversationId.toString(), expectedValue)

        val calls = callRepository.callsFlow()
        assertEquals(mapOfCallProfiles.size, calls.first().size)
        assertEquals(calls.first()[0].isMuted, expectedValue)
    }

    @Test
    fun givenAConversationIdThatDoesNotExistsInTheFlow_whenUpdateIsCameraOnIdIsCalled_thenDoNotUpdateTheFlow() = runTest {
        callRepository.updateIsCameraOnById(randomConversationIdString, false)

        val calls = callRepository.callsFlow()
        assertEquals(0, calls.first().size)
    }

    @Test
    fun givenAConversationIdThatExistsInTheFlow_whenUpdateIsCameraOnByIdIsCalled_thenUpdateCallStatusInTheFlow() = runTest {
        val expectedValue = true
        callRepository.updateCallProfileFlow(CallProfile(mapOfCallProfiles))

        callRepository.updateIsCameraOnById(startedCall.conversationId.toString(), expectedValue)

        val calls = callRepository.callsFlow()
        assertEquals(mapOfCallProfiles.size, calls.first().size)
        assertEquals(calls.first()[0].isCameraOn, expectedValue)
    }

    @Test
    fun givenNoIncomingCallsInTheFlow_whenGetIncomingCallsIsCalled_thenReturnAnEmptyListInTheFlow() = runTest {
        val calls = callRepository.incomingCallsFlow()

        assertEquals(0, calls.first().size)
    }

    @Test
    fun givenSomeIncomingCallsInTheFlow_whenGetIncomingCallsIsCalled_thenReturnTheListOfIncomingCallsInTheFlow() = runTest {
        callRepository.updateCallProfileFlow(CallProfile(mapOfCallProfiles))

        val calls = callRepository.incomingCallsFlow()

        assertEquals(1, calls.first().size)
        assertEquals(calls.first()[0], incomingCall)
    }

    @Test
    fun givenNoOngoingCallsInTheFlow_whenGetOngoingCallIsCalled_thenReturnAnEmptyListInTheFlow() = runTest {
        val calls = callRepository.incomingCallsFlow()

        assertEquals(0, calls.first().size)
    }

    @Test
    fun givenSomeEstablishedCallsInTheFlow_whenGetEstablishedCallIsCalled_thenReturnTheListOfOngoingCallsInTheFlow() = runTest {
        callRepository.updateCallProfileFlow(CallProfile(mapOfCallProfiles))

        val calls = callRepository.establishedCallsFlow()

        assertEquals(2, calls.first().size)
        assertEquals(calls.first()[0], establishedCall)
        assertEquals(calls.first()[1], answeredCall)
    }

    @Test
    fun givenNewCallParticipants_whenUpdatingParticipants_thenCallProfileIsUpdated() = runTest {
        callRepository.updateCallProfileFlow(
            CallProfile(
                mapOf(establishedCall.conversationId.toString() to establishedCall)
            )
        )

        val expectedParticipants = listOf(
            Participant(
                id = QualifiedID(
                    value = "value1",
                    domain = "domain1"
                ),
                clientId = "clientid",
                isMuted = true
            )
        )

        val calls = callRepository.callsFlow()

        callRepository.updateCallParticipants(
            conversationId = establishedCall.conversationId.toString(),
            participants = expectedParticipants
        )

        assertEquals(expectedParticipants, calls.first()[0].participants)
    }

    @Test
    fun givenActiveSpeakers_whenUpdatingActiveSpeakers_thenCallProfileIsUpdated() = runTest {
        callRepository.updateCallProfileFlow(
            CallProfile(
                mapOf(establishedCall.conversationId.toString() to establishedCall)
            )
        )

        val calls = callRepository.callsFlow()

        val participants = listOf(
            Participant(
                id = QualifiedID(
                    value = "value1",
                    domain = "domain1"
                ),
                clientId = "clientid",
                isMuted = false,
                isSpeaking = false
            )
        )

        callRepository.updateCallParticipants(
            conversationId = establishedCall.conversationId.toString(),
            participants = participants
        )

        val expectedActiveSpeakers = CallActiveSpeakers(
            activeSpeakers = listOf(
                CallActiveSpeaker(
                    userId = "value1@domain1",
                    clientId = "clientid",
                    audioLevel = 1,
                    audioLevelNow = 1
                )
            )
        )

        callRepository.updateParticipantsActiveSpeaker(
            conversationId = establishedCall.conversationId.toString(),
            activeSpeakers = expectedActiveSpeakers
        )

        assertEquals(true, calls.first()[0].participants.first().isSpeaking)
    }

    @Test
    fun givenIncomingCall_whenUpdateStatusToEstablished_thenCallEstablishedTimeUpdated() = runTest {
        given(conversationRepository).suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(any())
            .thenReturn(flowOf(ConversationDetails.Group(TestConversation.ONE_ON_ONE, LegalHoldStatus.ENABLED)))
        given(userRepository).suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(flowOf(TestUser.OTHER))
        given(userRepository).suspendFunction(userRepository::getSelfUserId)
            .whenInvoked()
            .thenReturn(TestUser.USER_ID)
        given(teamRepository).suspendFunction(teamRepository::getTeam)
            .whenInvokedWith(any())
            .thenReturn(flowOf(Team("team1", "team_1")))
        given(persistMessage).suspendFunction(persistMessage::invoke)
            .whenInvokedWith(any())
            .thenReturn(Either.Right(Unit))
        callRepository.createCall(conversationIdIncomingCall, CallStatus.INCOMING, "caller_id", false, false)

        val calls = callRepository.callsFlow()

        callRepository.updateCallStatusById(conversationIdIncomingCall.toString(), CallStatus.ESTABLISHED)

        calls.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertTrue(list[0].establishedTime != null)
        }

        verify(persistMessage)
            .suspendFunction(persistMessage::invoke)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenCallWithoutEstablishedTime_whenUpdateStatusToClose_thenMissedCallAddedToDB() = runTest {
        given(conversationRepository).suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(any())
            .thenReturn(flowOf(ConversationDetails.Group(TestConversation.ONE_ON_ONE, LegalHoldStatus.ENABLED)))
        given(userRepository).suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(flowOf(TestUser.OTHER))
        given(userRepository).suspendFunction(userRepository::getSelfUserId)
            .whenInvoked()
            .thenReturn(TestUser.USER_ID)
        given(teamRepository).suspendFunction(teamRepository::getTeam)
            .whenInvokedWith(any())
            .thenReturn(flowOf(Team("team1", "team_1")))
        given(persistMessage).suspendFunction(persistMessage::invoke)
            .whenInvokedWith(any())
            .thenReturn(Either.Right(Unit))
        callRepository.createCall(conversationIdIncomingCall, CallStatus.INCOMING, "caller_id", false, false)

        callRepository.updateCallStatusById(conversationIdIncomingCall.toString(), CallStatus.CLOSED)

        verify(persistMessage)
            .suspendFunction(persistMessage::invoke)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenCallWithEstablishedTime_whenUpdateStatusToClose_thenMissedCallNotAddedToDB() = runTest {
        given(conversationRepository).suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(any())
            .thenReturn(flowOf(ConversationDetails.Group(TestConversation.ONE_ON_ONE, LegalHoldStatus.ENABLED)))
        given(userRepository).suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(flowOf(TestUser.OTHER))
        given(userRepository).suspendFunction(userRepository::getSelfUserId)
            .whenInvoked()
            .thenReturn(TestUser.USER_ID)
        given(teamRepository).suspendFunction(teamRepository::getTeam)
            .whenInvokedWith(any())
            .thenReturn(flowOf(Team("team1", "team_1")))
        given(persistMessage).suspendFunction(persistMessage::invoke)
            .whenInvokedWith(any())
            .thenReturn(Either.Right(Unit))
        callRepository.createCall(conversationIdIncomingCall, CallStatus.INCOMING, "caller_id", false, false)
        callRepository.updateCallStatusById(conversationIdIncomingCall.toString(), CallStatus.ESTABLISHED)

        callRepository.updateCallStatusById(conversationIdIncomingCall.toString(), CallStatus.CLOSED)

        verify(persistMessage)
            .suspendFunction(persistMessage::invoke)
            .with(any())
            .wasNotInvoked()
    }

    private fun provideCall(id: ConversationId, status: CallStatus) = Call(
        conversationId = id,
        status = status,
        callerId = "caller_id@domain",
        participants = listOf(),
        isMuted = false,
        isCameraOn = false,
        maxParticipants = 0,
        conversationName = "ONE_ON_ONE Name",
        conversationType = Conversation.Type.ONE_ON_ONE,
        callerName = "otherUsername",
        callerTeamName = "team_1"
    )

    private companion object {
        const val CALL_CONFIG_API_RESPONSE = "{'call':'success','config':'dummy_config'}"
        private const val randomConversationIdString = "random@domain"
        private val sharedConversationId = ConversationId("value", "domain")
        private val conversationIdAnsweredCall = ConversationId("value2", "domain2")
        private val conversationIdIncomingCall = ConversationId("value3", "domain3")
        private val conversationIdEstablishedCall = ConversationId("value4", "domain4")
    }
}
