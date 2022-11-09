package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.message.MigratedMessage
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.conversation.message.ApplicationMessageHandler
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.messages.GenericMessage
import com.wire.kalium.protobuf.messages.Text
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PersistMigratedMessagesUseCaseTest {

    @Test
    fun givenAValidProtoMessage_whenMigratingMessage_thenShouldReturnASuccessResult() = runTest {
        // Given
        val (arrangement, persistMigratedMessages) = Arrangement()
            .withSuccessfulHandling()
            .arrange()

        // When
        val result = persistMigratedMessages(listOf(arrangement.fakeMigratedMessage()))

        // Then
        assertTrue(result is Either.Right)
    }

    private class Arrangement {

        @Mock
        private val persistMessage = mock(classOf<PersistMessageUseCase>())

        @Mock
        private val applicationMessageHandler = mock(classOf<ApplicationMessageHandler>())

        val genericMessage = GenericMessage("uuid", GenericMessage.Content.Text(Text("some_text")))

        fun fakeMigratedMessage() = MigratedMessage(
            conversationId = TestConversation.ID,
            senderUserId = TestUser.USER_ID,
            senderClientId = TestClient.CLIENT_ID,
            "",
            "some_content",
            genericMessage.encodeToByteArray()
        )

        fun withSuccessfulHandling(): Arrangement {
            given(applicationMessageHandler)
                .suspendFunction(applicationMessageHandler::handleContent)
                .whenInvokedWith(any(), any(), any(), any(), any())
                .thenReturn(Unit)
            return this
        }

        fun arrange() = this to PersistMigratedMessagesUseCaseImpl(
            applicationMessageHandler,
            MapperProvider.protoContentMapper()
        )
    }

}
