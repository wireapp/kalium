package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue


@OptIn(ExperimentalCoroutinesApi::class)
class SendKnockUserCaseTest {

    @Test
    fun givenAValidSendKnockRequest_whenSendingKnock_thenShouldReturnASuccessResult() = runTest {
        // Given
        val conversationId = ConversationId("some-convo-id", "some-domain-id")
        val (_, sendKnockUseCase) = Arrangement()
            .withSuccessfulResponse(false)
            .arrange()

        // When
        val result =
            sendKnockUseCase.invoke(conversationId, false)

        // Then
        assertTrue(result is Either.Right)
    }

    private class Arrangement {

        @Mock
        val persistMessage = mock(classOf<PersistMessageUseCase>())

        @Mock
        private val userRepository = mock(classOf<UserRepository>())

        @Mock
        private val clientRepository = mock(classOf<ClientRepository>())

        @Mock
        val messageSender = mock(classOf<MessageSender>())

        val someClientId = ClientId("some-client-id")

        private fun fakeSelfUser() = SelfUser(
            UserId("some_id", "some_domain"),
            "some_name",
            "some_handle",
            "some_email",
            null,
            1,
            null,
            ConnectionState.ACCEPTED,
            previewPicture = UserAssetId("value1", "domain"),
            completePicture = UserAssetId("value2", "domain"),
            UserAvailabilityStatus.NONE
        )

        fun withSuccessfulResponse(hotKnock: Boolean): Arrangement {
            given(userRepository)
                .suspendFunction(userRepository::observeSelfUser)
                .whenInvoked()
                .thenReturn(flowOf(fakeSelfUser()))
            given(clientRepository)
                .suspendFunction(clientRepository::currentClientId)
                .whenInvoked()
                .thenReturn(Either.Right(someClientId))
            given(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
            given(messageSender)
                .suspendFunction(messageSender::sendPendingMessage)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
            return this
        }

        fun arrange() = this to SendKnockUseCase(
            persistMessage,
            userRepository,
            clientRepository,
            messageSender
        )
    }

}
