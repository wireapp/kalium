package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallStatus
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class IsCallRunningUseCaseTest {

    @Mock
    private val callRepository = mock(classOf<CallRepository>())

    private lateinit var isCallRunningUseCase: IsCallRunningUseCase

    @BeforeTest
    fun setUp() {
        isCallRunningUseCase = IsCallRunningUseCase(
            callRepository = callRepository
        )
    }

    @Test
    fun givenAFlowWithEmptyValues_whenInvokingUseCase_thenReturnsFalse() = runTest {
        given(callRepository)
            .suspendFunction(callRepository::callsFlow).whenInvoked()
            .thenReturn(flowOf(listOf()))

        val result = isCallRunningUseCase()

        assertEquals(false, result)
    }

    @Test
    fun givenAFlowThatDoesNotContainIncomingOrOutgoingOrOngoingCall_whenInvokingUseCase_thenReturnsFalse() = runTest {
        given(callRepository)
            .suspendFunction(callRepository::callsFlow).whenInvoked()
            .thenReturn(flowOf(listOf(call2)))

        val result = isCallRunningUseCase()

        assertEquals(false, result)
    }

    @Test
    fun givenAFlowContainingAnIncomingCall_whenInvokingUseCase_thenReturnsTrue() = runTest {
        given(callRepository)
            .suspendFunction(callRepository::callsFlow).whenInvoked()
            .thenReturn(flowOf(listOf(call1, call2)))

        val result = isCallRunningUseCase()

        assertEquals(true, result)
    }

    companion object {
        private val call1 = Call(
            ConversationId("first", "domain"),
            CallStatus.STARTED,
            true,
            false,
            "caller-id1",
            "ONE_ON_ONE Name",
            Conversation.Type.ONE_ON_ONE,
            "otherUsername",
            "team1"
        )
        private val call2 = Call(
            ConversationId("second", "domain"),
            CallStatus.CLOSED,
            true,
            false,
            "caller-id2",
            "ONE_ON_ONE Name",
            Conversation.Type.ONE_ON_ONE,
            "otherUsername",
            "team1"
        )
    }

}
