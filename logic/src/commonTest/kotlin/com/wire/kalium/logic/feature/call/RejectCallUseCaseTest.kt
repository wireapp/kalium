package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.usecase.RejectCallUseCase
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class RejectCallUseCaseTest {

    @Mock
    private val callManager = mock(classOf<CallManager>())

    @Mock
    private val callRepository = mock(classOf<CallRepository>())

    private lateinit var rejectCallUseCase: RejectCallUseCase

    @BeforeTest
    fun setup() {
        rejectCallUseCase = RejectCallUseCase(lazy{ callManager }, callRepository)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenCallingParams_whenRunningUseCase_thenInvokeRejectCallOnce() = runTest {
        val conversationId = ConversationId("someone", "wire.com")

        given(callManager)
            .suspendFunction(callManager::rejectCall)
            .whenInvokedWith(eq(conversationId))
            .thenDoNothing()

        given(callRepository)
            .suspendFunction(callRepository::updateCallStatusById)
            .whenInvokedWith(eq(conversationId.toString()), eq(CallStatus.REJECTED))
            .thenDoNothing()

        rejectCallUseCase.invoke(conversationId)

        verify(callManager)
            .suspendFunction(callManager::rejectCall)
            .with(eq(conversationId))
            .wasInvoked(once)

        verify(callRepository)
            .suspendFunction(callRepository::updateCallStatusById)
            .with(eq(conversationId.toString()),  eq(CallStatus.REJECTED))
            .wasInvoked(once)
    }

}
