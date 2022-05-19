package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.LegalHoldStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.feature.call.usecase.GetAllCallsUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.SyncManager
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
    fun givenAFlowOfCalls_whenUseCaseInvoked_thenReturnThatFlow() = runTest {
        given(syncManager).coroutine { waitForSlowSyncToComplete() }
            .thenReturn(Unit)
        given(callRepository).invocation { callsFlow() }
            .then { MutableStateFlow(calls) }

        val result = getAllCallsUseCase()

        assertEquals(calls, result.first())
    }

    companion object {
        private val calls = listOf(
            Call(
                ConversationId("first", "domain"),
                CallStatus.STARTED,
                "caller-id",
                "ONE_ON_ONE Name",
                Conversation.Type.ONE_ON_ONE,
                "otherUsername",
                "team1"
            ),
            Call(
                ConversationId("second", "domain"),
                CallStatus.INCOMING,
                "caller-id",
                "ONE_ON_ONE Name",
                Conversation.Type.ONE_ON_ONE,
                "otherUsername2",
                "team2"
            )
        )
    }

}
