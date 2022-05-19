package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.usecase.EndCallUseCase
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

class EndCallUseCaseTest {

    @Mock
    private val callManager = mock(classOf<CallManager>())

    private lateinit var endCall: EndCallUseCase

    @BeforeTest
    fun setup() {
        endCall = EndCallUseCase(lazy{ callManager })
    }

    @Test
    fun givenCallingParams_whenRunningUseCase_thenInvokeEndCallOnce() = runTest {
        val conversationId = ConversationId("someone", "wire.com")

        given(callManager)
            .suspendFunction(callManager::endCall)
            .whenInvokedWith(eq(conversationId))
            .thenDoNothing()

        endCall.invoke(conversationId)

        verify(callManager)
            .suspendFunction(callManager::endCall)
            .with(eq(conversationId))
            .wasInvoked(once)
    }

}
