package com.wire.kalium.logic.feature.call

import com.wire.kalium.calling.CallType
import com.wire.kalium.calling.CallingConversationType
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.usesCases.StartCallUseCase
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
        startCall = StartCallUseCase(callManager)
    }

    @Test
    fun givenCallingParams_whenRunningUseCase_thenInvokeStartCallOnce() = runTest {
        val conversationId = ConversationId("someone", "wire.com")

        given(callManager)
            .suspendFunction(callManager::startCall)
            .whenInvokedWith(eq(conversationId), eq(CallType.NORMAL.value), eq(CallingConversationType.OneOnOne.value), eq(false))
            .thenDoNothing()

        startCall.invoke(conversationId, CallType.NORMAL, CallingConversationType.OneOnOne)

        verify(callManager)
            .suspendFunction(callManager::startCall)
            .with(eq(conversationId), eq(CallType.NORMAL.value), eq(CallingConversationType.OneOnOne.value), eq(false))
            .wasInvoked(once)
    }

}
