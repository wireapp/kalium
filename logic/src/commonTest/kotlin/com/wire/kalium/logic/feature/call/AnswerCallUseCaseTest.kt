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
    private val callManagerImpl = mock(classOf<CallManager>())

    private lateinit var answerCallUseCase: AnswerCallUseCase

    @BeforeTest
    fun setUp() {
        answerCallUseCase = AnswerCallUseCaseImpl(
            callManagerImpl = callManagerImpl
        )
    }

    @Test
    fun givenAnIncomingCallIsReceived_whenAnsweringTheIncomingCall_thenCallManagerIsCalled() = runTest {
        val conversationId = ConversationId(
            value = "value1",
            domain = "domain1"
        )

        given(callManagerImpl)
            .suspendFunction(callManagerImpl::answerCall)
            .whenInvokedWith(eq(conversationId))
            .thenDoNothing()

        answerCallUseCase.invoke(
            conversationId = conversationId
        )

        verify(callManagerImpl)
            .suspendFunction(callManagerImpl::answerCall)
            .with(eq(conversationId))
            .wasInvoked(exactly = once)
    }
}
