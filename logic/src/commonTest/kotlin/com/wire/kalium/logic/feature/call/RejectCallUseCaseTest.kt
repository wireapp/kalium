package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.usecase.RejectCallUseCase
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

class RejectCallUseCaseTest {

    @Mock
    private val callManager = mock(classOf<CallManager>())

    private lateinit var rejectCallUseCase: RejectCallUseCase

    @BeforeTest
    fun setup() {
        rejectCallUseCase = RejectCallUseCase(callManager)
    }

    @Test
    fun givenCallingParams_whenRunningUseCase_thenInvokeRejectCallOnce() = runTest {
        val conversationId = ConversationId("someone", "wire.com")

        given(callManager)
            .suspendFunction(callManager::rejectCall)
            .whenInvokedWith(eq(conversationId))
            .thenDoNothing()

        rejectCallUseCase.invoke(conversationId)

        verify(callManager)
            .suspendFunction(callManager::rejectCall)
            .with(eq(conversationId))
            .wasInvoked(once)
    }

}
