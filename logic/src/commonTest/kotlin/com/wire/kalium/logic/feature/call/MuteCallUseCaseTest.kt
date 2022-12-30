package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.usecase.MuteCallUseCase
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MuteCallUseCaseTest {

    @Mock
    private val callManager = mock(classOf<CallManager>())

    @Mock
    private val callRepository = mock(classOf<CallRepository>())

    private lateinit var muteCall: MuteCallUseCase

    @BeforeTest
    fun setup() {
        muteCall = MuteCallUseCase(lazy { callManager }, callRepository)

        given(callManager)
            .suspendFunction(callManager::muteCall)
            .whenInvokedWith(eq(isMuted))
            .thenDoNothing()

        given(callRepository)
            .function(callRepository::updateIsMutedById)
            .whenInvokedWith(eq(conversationId.toString()), eq(isMuted))
            .thenDoNothing()
    }

    @Test
    fun givenAnEstablishedCallWhenMuteUseCaseCalledThenUpdateMuteStateAndMuteCall() = runTest {
        given(callRepository)
            .suspendFunction(callRepository::establishedCallsFlow)
            .whenInvoked().then {
                flowOf(listOf(call))
            }

        muteCall(conversationId)

        verify(callRepository)
            .function(callRepository::updateIsMutedById)
            .with(eq(conversationId.toString()), eq(isMuted))
            .wasInvoked(once)

        verify(callManager)
            .suspendFunction(callManager::muteCall)
            .with(eq(isMuted))
            .wasInvoked(once)
    }

    @Test
    fun givenNonEstablishedCallWhenMuteUseCaseCalledThenUpdateMuteStateOnly() = runTest {
        given(callRepository)
            .suspendFunction(callRepository::establishedCallsFlow)
            .whenInvoked().then {
                flowOf(listOf())
            }

        muteCall(conversationId)

        verify(callRepository)
            .function(callRepository::updateIsMutedById)
            .with(eq(conversationId.toString()), eq(isMuted))
            .wasInvoked(once)

        verify(callManager)
            .suspendFunction(callManager::muteCall)
            .with(eq(isMuted))
            .wasNotInvoked()
    }

    companion object {
        const val isMuted = true
        private val conversationId = ConversationId("value", "domain")
        val call = Call(
            conversationId = conversationId,
            status = CallStatus.ESTABLISHED,
            callerId = "called-id",
            isMuted = false,
            isCameraOn = false,
            conversationName = null,
            conversationType = Conversation.Type.GROUP,
            callerName = null,
            callerTeamName = null,
            establishedTime = null
        )
    }
}
