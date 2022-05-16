package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.call.CallApi
import com.wire.kalium.network.utils.NetworkResponse
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.oneOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CallRepositoryTest {

    @Mock
    private val callApi = mock(classOf<CallApi>())

    private lateinit var callRepository: CallRepository

    @BeforeTest
    fun setUp() {
        callRepository = CallDataSource(
            callApi = callApi
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
    fun givenEmptyListOfCalls_whenGetAllCallsIsCalled_thenReturnAnEmptyListOfCalls() {
        val calls = callRepository.getAllCalls()

        assertEquals(0, calls.value.size)
    }


    @Test
    fun givenAListOfCallProfiles_whenGetAllCallsIsCalled_thenReturnAnEmptyListOfCalls() {
        callRepository.updateCallProfileFlow(
            CallProfile(
                mapOf(
                    startedCall.conversationId.toString() to startedCall,
                    answeredCall.conversationId.toString() to answeredCall,
                    incomingCall.conversationId.toString() to incomingCall,
                    establishedCall.conversationId.toString() to establishedCall
                )
            )
        )

        val calls = callRepository.getAllCalls()

        assertEquals(4, calls.value.size)
        assertEquals(startedCall, calls.value[0])
        assertEquals(establishedCall, calls.value[3])
    }


    @Test
    fun givenACallObject_whenCreateCallCalled_thenAddThatCallToTheFlow() {
        callRepository.updateCallProfileFlow(CallProfile(mapOf(startedCall.conversationId.toString() to startedCall)))

        callRepository.createCall(answeredCall)

        val calls = callRepository.getAllCalls()
        assertEquals(2, calls.value.size)
        assertEquals(calls.value[0], startedCall)
        assertEquals(calls.value[1], answeredCall)
    }

    @Test
    fun givenCallObjectWithSameConversationIdAsAnotherInTheFlow_whenCreateCallCalled_thenEraseTheOldOneWithTheNewer() {
        callRepository.updateCallProfileFlow(CallProfile(mapOf(startedCall.conversationId.toString() to startedCall)))

        callRepository.createCall(incomingCall2)

        val calls = callRepository.getAllCalls()
        assertEquals(1, calls.value.size)
        assertEquals(calls.value[0], incomingCall2)
    }

    @Test
    fun givenAConversationIdThatDoesNotExistsInTheFlow_whenUpdateCallStatusIsCalled_thenDoNotUpdateTheFlow() {
        callRepository.updateCallStatusById(randomConversationIdString, CallStatus.INCOMING)

        val calls = callRepository.getAllCalls()
        assertEquals(0, calls.value.size)
    }

    @Test
    fun givenAConversationIdThatExistsInTheFlow_whenUpdateCallStatusIsCalled_thenUpdateCallStatusInTheFlow() {
        callRepository.updateCallProfileFlow(CallProfile(mapOf(startedCall.conversationId.toString() to startedCall)))

        callRepository.updateCallStatusById(startedCall.conversationId.toString(), CallStatus.ESTABLISHED)

        val calls = callRepository.getAllCalls()
        assertEquals(1, calls.value.size)
        assertEquals(calls.value[0].status, CallStatus.ESTABLISHED)
    }

    @Test
    fun givenNoIncomingCallsInTheFlow_whenGetIncomingCallsIsCalled_thenReturnAnEmptyListInTheFlow() = runTest {
        val calls = callRepository.getIncomingCalls()

        assertEquals(0, calls.first().size)
    }

    @Test
    fun givenSomeIncomingCallsInTheFlow_whenGetIncomingCallsIsCalled_thenReturnTheListOfIncomingCallsInTheFlow() = runTest {
        callRepository.updateCallProfileFlow(
            CallProfile(
                mapOf(
                    startedCall.conversationId.toString() to startedCall,
                    answeredCall.conversationId.toString() to answeredCall,
                    incomingCall.conversationId.toString() to incomingCall
                )
            )
        )

        val calls = callRepository.getIncomingCalls()

        assertEquals(1, calls.first().size)
        assertEquals(calls.first()[0], incomingCall)
    }

    @Test
    fun givenNoOngoingCallsInTheFlow_whenGetOngoingCallIsCalled_thenReturnAnEmptyListInTheFlow() = runTest {
        val calls = callRepository.getIncomingCalls()

        assertEquals(0, calls.first().size)
    }

    @Test
    fun givenSomeOngoingCallsInTheFlow_whenGetOngoingCallIsCalled_thenReturnTheListOfOngoingCallsInTheFlow() = runTest {
        callRepository.updateCallProfileFlow(
            CallProfile(
                mapOf(
                    startedCall.conversationId.toString() to startedCall,
                    answeredCall.conversationId.toString() to answeredCall,
                    incomingCall.conversationId.toString() to incomingCall,
                    establishedCall.conversationId.toString() to establishedCall
                )
            )
        )

        val calls = callRepository.getOngoingCall()

        assertEquals(2, calls.first().size)
        assertEquals(calls.first()[0], answeredCall)
        assertEquals(calls.first()[1], establishedCall)
    }

    private companion object {
        const val CALL_CONFIG_API_RESPONSE = "{'call':'success','config':'dummy_config'}"
        private const val randomConversationIdString = "random@domain"
        private val conversationIdStartedCall = ConversationId("value1", "domain1")
        private val conversationIdAnsweredCall = ConversationId("value2", "domain2")
        private val conversationIdIncomingCall = ConversationId("value3", "domain3")
        private val conversationIdEstablishedCall = ConversationId("value4", "domain4")

        private val startedCall = Call(
            conversationId = conversationIdStartedCall,
            status = CallStatus.STARTED,
            callerId = "caller-id",
            participants = listOf(),
            maxParticipants = 0
        )

        private val answeredCall = Call(
            conversationId = conversationIdAnsweredCall,
            status = CallStatus.ANSWERED,
            callerId = "caller-id",
            participants = listOf(),
            maxParticipants = 0
        )

        private val incomingCall = Call(
            conversationId = conversationIdIncomingCall,
            status = CallStatus.INCOMING,
            callerId = "caller-id",
            participants = listOf(),
            maxParticipants = 0
        )

        private val incomingCall2 = Call(
            conversationId = conversationIdStartedCall,
            status = CallStatus.INCOMING,
            callerId = "caller-id",
            participants = listOf(),
            maxParticipants = 0
        )

        private val establishedCall = Call(
            conversationId = conversationIdEstablishedCall,
            status = CallStatus.ESTABLISHED,
            callerId = "caller-id",
            participants = listOf(),
            maxParticipants = 0
        )
    }
}
