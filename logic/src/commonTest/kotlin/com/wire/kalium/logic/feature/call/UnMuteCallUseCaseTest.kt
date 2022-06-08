package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.usecase.MuteCallUseCase
import com.wire.kalium.logic.feature.call.usecase.UnMuteCallUseCase
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

class UnMuteCallUseCaseTest {

    @Mock
    private val callManager = mock(classOf<CallManager>())

    @Mock
    private val callRepository = mock(classOf<CallRepository>())

    private lateinit var unMuteCall: UnMuteCallUseCase

    @BeforeTest
    fun setup() {
        unMuteCall = UnMuteCallUseCase(lazy { callManager }, callRepository)
    }

    @Test
    fun `givenACallManagerWhenUseCaseIsInvokedThenUnMuteCall`() = runTest {
        val isMuted = false
        given(callManager)
            .suspendFunction(callManager::muteCall)
            .whenInvokedWith(eq(isMuted))
            .thenDoNothing()
        given(callRepository)
            .function(callRepository::updateIsMutedById)
            .whenInvokedWith(eq(conversationId.toString()), eq(isMuted))
            .thenDoNothing()

        unMuteCall(conversationId)

        verify(callManager)
            .suspendFunction(callManager::muteCall)
            .with(eq(isMuted))
            .wasInvoked(once)

        verify(callRepository)
            .function(callRepository::updateIsMutedById)
            .with(eq(conversationId.toString()), eq(isMuted))
            .wasInvoked(once)
    }

    companion object {
        private val conversationId = ConversationId("value", "domain")
    }
}
