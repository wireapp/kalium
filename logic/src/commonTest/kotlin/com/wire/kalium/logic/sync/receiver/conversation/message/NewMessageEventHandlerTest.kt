package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test

class NewMessageEventHandlerTest {

    @Test
    fun givenProteusEvent_whenHandling_shouldAskProteusUnpackerToDecrypt() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withProteusUnpackerReturning(Either.Right(MessageUnpackResult.ProtocolSignalingMessage))
            .arrange()

        val newMessageEvent = TestEvent.newMessageEvent("encryptedContent")

        newMessageEventHandler.handleNewProteusMessage(newMessageEvent)

        verify(arrangement.proteusMessageUnpacker)
            .suspendFunction(arrangement.proteusMessageUnpacker::unpackProteusMessage)
            .with(eq(newMessageEvent))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMLSEvent_whenHandling_shouldAskMLSUnpackerToDecrypt() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement()
            .withMLSUnpackerReturning(Either.Right(MessageUnpackResult.ProtocolSignalingMessage))
            .arrange()

        val newMessageEvent = TestEvent.newMLSMessageEvent(Clock.System.now())

        newMessageEventHandler.handleNewMLSMessage(newMessageEvent)

        verify(arrangement.mlsMessageUnpacker)
            .suspendFunction(arrangement.mlsMessageUnpacker::unpackMlsMessage)
            .with(eq(newMessageEvent))
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val proteusMessageUnpacker = mock(classOf<ProteusMessageUnpacker>())

        @Mock
        val mlsMessageUnpacker = mock(classOf<MLSMessageUnpacker>())

        @Mock
        val applicationMessageHandler = configure(mock(classOf<ApplicationMessageHandler>())) {
            stubsUnitByDefault = true
        }

        private val newMessageEventHandler: NewMessageEventHandler = NewMessageEventHandlerImpl(
            proteusMessageUnpacker, mlsMessageUnpacker, applicationMessageHandler
        )

        fun withProteusUnpackerReturning(result: Either<CoreFailure, MessageUnpackResult>) = apply {
            given(proteusMessageUnpacker)
                .suspendFunction(proteusMessageUnpacker::unpackProteusMessage)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withMLSUnpackerReturning(result: Either<CoreFailure, MessageUnpackResult>) = apply {
            given(mlsMessageUnpacker)
                .suspendFunction(mlsMessageUnpacker::unpackMlsMessage)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun arrange() = this to newMessageEventHandler

    }
}
