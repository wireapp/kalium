package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.VideoState
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.usecase.UpdateVideoStateUseCase
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class UpdateVideoStateUseCaseTest {

    @Mock
    private val callManager = mock(classOf<CallManager>())

    private lateinit var updateVideoStateUseCase: UpdateVideoStateUseCase

    @BeforeTest
    fun setup() {
        updateVideoStateUseCase = UpdateVideoStateUseCase(callManager)
    }

    @Test
    fun givenAValidConversationIdAndVideoState_whenUseCaseCalled_thenInvokeOnceUseCase() = runTest {
        val conversationId = ConversationId("default", "domain")
        val videoState = VideoState.STARTED

        given(callManager)
            .suspendFunction(callManager::updateVideoState)
            .whenInvokedWith(eq(conversationId), eq(videoState))
            .thenDoNothing()

        updateVideoStateUseCase(conversationId, videoState)

        verify(callManager)
            .suspendFunction(callManager::updateVideoState)
            .with(eq(conversationId), eq(videoState))
            .wasInvoked(once)
    }

}
