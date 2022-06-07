package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.VideoState
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.usecase.UpdateVideoStateUseCase
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class UpdateVideoStateUseCaseTest {

    @Mock
    private val callManager = mock(classOf<CallManager>())

    @Mock
    private val callRepository = mock(classOf<CallRepository>())

    private lateinit var updateVideoStateUseCase: UpdateVideoStateUseCase

    @BeforeTest
    fun setup() {
        updateVideoStateUseCase = UpdateVideoStateUseCase(lazy { callManager }, callRepository)
        given(callRepository)
            .function(callRepository::updateIsCameraOnById)
            .whenInvokedWith(eq(conversationId.toString()), eq(isCameraOn))
            .thenDoNothing()

    }

    @Test
    fun givenAFlowOfCallsThatContainsAnEstablishedCall_whenUseCaseInvoked_thenInvokeUpdateVideoState() = runTest {
        val establishedCall = Call(
            conversationId,
            CallStatus.ESTABLISHED,
            isMuted = true,
            isCameraOn = true,
            callerId = "caller-id",
            conversationName = "",
            Conversation.Type.ONE_ON_ONE,
            null,
            null
        )
        given(callManager)
            .suspendFunction(callManager::updateVideoState)
            .whenInvokedWith(eq(conversationId), eq(videoState))
            .thenDoNothing()

        given(callRepository)
            .function(callRepository::callsFlow)
            .whenInvoked().then {
                flowOf(listOf(establishedCall))
            }
        given(callRepository)
            .function(callRepository::updateIsCameraOnById)
            .whenInvokedWith(eq(conversationId.toString()), eq(isCameraOn))
            .thenDoNothing()

        updateVideoStateUseCase(conversationId, videoState)

        verify(callRepository)
            .function(callRepository::updateIsCameraOnById)
            .with(eq(conversationId.toString()), eq(isCameraOn))
            .wasInvoked(once)

        verify(callManager)
            .suspendFunction(callManager::updateVideoState)
            .with(any(), any())
            .wasInvoked(once)
    }

    @Test
    fun givenAFlowOfCallsThatContainsNonEstablishedCall_whenUseCaseInvoked_thenDoNotInvokeUpdateVideoState() = runTest {
        val startedCall = Call(
            conversationId,
            CallStatus.STARTED,
            isMuted = true,
            isCameraOn = true,
            callerId = "caller-id",
            "ONE_ON_ONE Name",
            Conversation.Type.ONE_ON_ONE,
            null,
            null
        )

        given(callRepository)
            .function(callRepository::callsFlow)
            .whenInvoked().then {
                flowOf(listOf(startedCall))
            }
        given(callRepository)
            .function(callRepository::updateIsCameraOnById)
            .whenInvokedWith(eq(conversationId.toString()), eq(isCameraOn))
            .thenDoNothing()

        updateVideoStateUseCase(conversationId, videoState)

        verify(callRepository)
            .function(callRepository::updateIsCameraOnById)
            .with(eq(conversationId.toString()), eq(isCameraOn))
            .wasInvoked(once)

        verify(callManager)
            .suspendFunction(callManager::updateVideoState)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenAFlowOfCallsWithADifferentIdFromCurrentCall_whenUseCaseInvoked_thenDoNotInvokeUpdateVideoState() = runTest {
        val randomCall = Call(
            ConversationId("different", "domain"),
            CallStatus.MISSED,
            isMuted = true,
            isCameraOn = true,
            callerId = "caller-id",
            "ONE_ON_ONE Name",
            Conversation.Type.ONE_ON_ONE,
            null,
            null
        )
        given(callRepository)
            .function(callRepository::callsFlow)
            .whenInvoked().then {
                flowOf(listOf(randomCall))
            }
        given(callRepository)
            .function(callRepository::updateIsCameraOnById)
            .whenInvokedWith(eq(conversationId.toString()), eq(isCameraOn))
            .thenDoNothing()

        updateVideoStateUseCase(conversationId, videoState)

        verify(callRepository)
            .function(callRepository::updateIsCameraOnById)
            .with(eq(conversationId.toString()), eq(isCameraOn))
            .wasInvoked(once)

        verify(callManager)
            .suspendFunction(callManager::updateVideoState)
            .with(any(), any())
            .wasNotInvoked()
    }

    companion object {
        private const val isCameraOn = true
        private val videoState = VideoState.STARTED
        private val conversationId = ConversationId("value", "domain")
    }

}
