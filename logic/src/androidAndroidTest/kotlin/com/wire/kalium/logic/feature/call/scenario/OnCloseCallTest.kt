package com.wire.kalium.logic.feature.call.scenario

import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.feature.call.CallStatus
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@ExperimentalCoroutinesApi
class OnCloseCallTest {

    @Mock
    private val callRepository: CallRepository = configure(mock(CallRepository::class)) {
        stubsUnitByDefault = true
    }
    @Mock
    private val qualifiedIdMapper = mock(classOf<QualifiedIdMapper>())

    private lateinit var onCloseCall: OnCloseCall

    private val testScope = TestScope()

    @BeforeTest
    fun setUp() {
        onCloseCall = OnCloseCall(
            callRepository = callRepository,
            scope = testScope,
            qualifiedIdMapper = qualifiedIdMapper
        )
        given(qualifiedIdMapper).invocation { fromStringToQualifiedID("conversationId@domainId") }
            .then { QualifiedID("conversationId", "domainId") }
    }

    @Suppress("FunctionNaming")
    @Test
    fun givenAConversationWithAnOngoingCall_whenClosingTheCallAndTheCallIsStillOngoing_thenVerifyTheStatusIsOngoing() = testScope.runTest {
        // given
        // when
        onCloseCall.onClosedCall(
            reason = 7,
            conversationId = "conversationId@domainId",
            messageTime = Uint32_t(value = 1),
            userId = "userId@domainId",
            clientId = "clientId",
            arg = null
        )
        advanceUntilIdle()

        // then
        verify(callRepository)
            .suspendFunction(callRepository::updateCallStatusById)
            .with(eq("conversationId@domainId"), eq(CallStatus.STILL_ONGOING))
            .wasInvoked(once)
    }

    @Suppress("FunctionNaming")
    @Test
    fun givenAConversationWithoutAnOngoingCall_whenClosingTheCallAndTheCallIsNotOngoing_thenVerifyTheStatusIsClosed() = testScope.runTest {
        // given
        // when
        onCloseCall.onClosedCall(
            reason = 0,
            conversationId = "conversationId@domainId",
            messageTime = Uint32_t(value = 1),
            userId = "userId@domainId",
            clientId = "clientId",
            arg = null
        )
        advanceUntilIdle()

        // then
        verify(callRepository)
            .suspendFunction(callRepository::updateCallStatusById)
            .with(eq("conversationId@domainId"), eq(CallStatus.CLOSED))
            .wasInvoked(once)
    }
}
