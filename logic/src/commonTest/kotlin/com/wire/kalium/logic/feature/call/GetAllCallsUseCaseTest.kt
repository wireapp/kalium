package com.wire.kalium.logic.feature.call

import app.cash.turbine.test
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.usecase.GetAllCallsUseCase
import com.wire.kalium.logic.sync.SyncManager
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetAllCallsUseCaseTest {

    @Mock
    private val callRepository = mock(classOf<CallRepository>())

    @Mock
    private val syncManager = mock(classOf<SyncManager>())

    private lateinit var getAllCallsUseCase: GetAllCallsUseCase

    @BeforeTest
    fun setUp() {
        getAllCallsUseCase = GetAllCallsUseCase(
            callRepository = callRepository,
            syncManager = syncManager
        )
    }

    @Test
    fun givenCallsFlowEmitsANewValue_whenUseCaseIsCollected_thenAssertThatTheUseCaseIsEmittingTheRightCalls() = runTest {
        val calls1 = listOf(call1, call2)
        val calls2 = listOf(call2)

        val callsFlow = flowOf(calls1, calls2)
        given(syncManager).coroutine { waitForSlowSyncToComplete() }
            .thenReturn(Unit)
        given(callRepository).invocation { callsFlow() }
            .then { callsFlow }

        val result = getAllCallsUseCase()

        result.test {
            assertEquals(calls1, awaitItem())
            assertEquals(calls2, awaitItem())
            awaitComplete()
        }
    }

    companion object {
        private val call1 = Call(
            ConversationId("first", "domain"),
            CallStatus.STARTED,
            "caller-id",
            "ONE_ON_ONE Name",
            Conversation.Type.ONE_ON_ONE,
            "otherUsername",
            "team1"
        )
        private val call2 = Call(
            ConversationId("second", "domain"),
            CallStatus.INCOMING,
            "caller-id",
            "ONE_ON_ONE Name",
            Conversation.Type.ONE_ON_ONE,
            "otherUsername2",
            "team2"
        )
    }

}
