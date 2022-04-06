package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.id.ConversationId
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

class AnswerCallUseCaseTest {

    @Mock
    private val callManager = mock(classOf<CallManager>())

    private lateinit var answerCallUseCase: AnswerCallUseCase

    @BeforeTest
    fun setUp() {
        answerCallUseCase = AnswerCallUseCaseImpl(
            callManager = callManager
        )
    }

    @Test
    fun givenAConversationId_whenAnsweringACallOfThatConversation_thenCallManagerIsCalledWithTheCorrectId() = runTest {
        val conversationId = ConversationId(
            value = "value1",
            domain = "domain1"
        )

        given(callManager)
            .suspendFunction(callManager::answerCall)
            .whenInvokedWith(eq(conversationId))
            .thenDoNothing()

        answerCallUseCase.invoke(
            conversationId = conversationId
        )

        verify(callManager)
            .suspendFunction(callManager::answerCall)
            .with(eq(conversationId))
            .wasInvoked(exactly = once)
    }
}
