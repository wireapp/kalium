package com.wire.kalium.logic.sync.receiver.conversation.message

import io.mockative.Mock
import io.mockative.classOf
import io.mockative.mock

class NewMessageEventHandlerTest {

    @Test
    fun givenNewProteusMessage_whenHandling_shouldCallProteusUnpacker() = runTest {
        val (arrangement, newMessageEventHandler) = Arrangement().arrange()

        // TODO(test): Add more tests
    }

    private class Arrangement {

        @Mock
        private val proteusMessageUnpacker = mock(classOf<ProteusMessageUnpacker>())

        @Mock
        private val mlsMessageUnpacker = mock(classOf<MLSMessageUnpacker>())

        @Mock
        private val applicationMessageHandler = mock(classOf<ApplicationMessageHandler>())


        private val newMessageEventHandler: NewMessageEventHandler = NewMessageEventHandlerImpl(
            proteusMessageUnpacker, mlsMessageUnpacker, applicationMessageHandler
        )

        fun arrange() = this to newMessageEventHandler
    }

}
