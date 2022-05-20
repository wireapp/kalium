package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.CallType
import com.wire.kalium.logic.data.call.ConversationType
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.usecase.StartCallUseCase
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

class StartCallUseCaseTest {

    @Mock
    private val callManager = mock(classOf<CallManager>())

    private lateinit var startCall: StartCallUseCase

    @BeforeTest
    fun setup() {
        startCall = StartCallUseCase(lazy{ callManager })
    }

    @Test
    fun givenCallingParams_whenRunningUseCase_thenInvokeStartCallOnce() = runTest {
        val conversationId = ConversationId("someone", "wire.com")

        given(callManager)
            .suspendFunction(callManager::startCall)
            .whenInvokedWith(eq(conversationId), eq(CallType.AUDIO), eq(ConversationType.OneOnOne), eq(false))
            .thenDoNothing()

        startCall.invoke(conversationId, CallType.AUDIO, ConversationType.OneOnOne)

        verify(callManager)
            .suspendFunction(callManager::startCall)
            .with(eq(conversationId), eq(CallType.AUDIO), eq(ConversationType.OneOnOne), eq(false))
            .wasInvoked(once)
    }

}
