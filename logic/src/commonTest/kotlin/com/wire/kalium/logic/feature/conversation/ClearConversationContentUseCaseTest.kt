package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.contact.search.UserSearchApi
import io.mockative.Mock
import io.mockative.Times
import io.mockative.anything
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertIs

class ClearConversationContentUseCaseTest {

    @Test
    fun givenClearConversationFails_whenInvoking_thenCorrectlyPropagateFailure() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withClearConversationContent(false)
            .withMessageSending(true)
            .withCurrentClientId((true))
            .withGetSelfConversationId()
            .withGetSelfUserId()
            .arrange()

        // when
        val result = useCase(ConversationId("someValue", "someDomain"))

        // then
        assertIs<ClearConversationContentUseCase.Result.Failure>(result)
    }

    fun givenGettingClientIdFails_whenInvoking_thenCorrectlyPropagateFailure() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withClearConversationContent(true)
            .withCurrentClientId(false)
            .withMessageSending(true)
            .withGetSelfConversationId()
            .withGetSelfUserId()
            .arrange()

        // when
        val result = useCase(ConversationId("someValue", "someDomain"))

        // then
        assertIs<ClearConversationContentUseCase.Result.Failure>(result)
    }

    @Test
    fun givenSendMessageFails_whenInvoking_thenCorrectlyPropagateFailure() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withClearConversationContent(true)
            .withCurrentClientId(true)
            .withMessageSending(false)
            .withGetSelfConversationId()
            .withGetSelfUserId()
            .arrange()

        // when
        val result = useCase(ConversationId("someValue", "someDomain"))

        // then
        assertIs<ClearConversationContentUseCase.Result.Failure>(result)
    }

    private class Arrangement {

        @Mock
        val conversationRepository: ConversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val assetRepository: AssetRepository = mock(classOf<AssetRepository>())

        @Mock
        val clearConversationContent: ClearConversationContent = mock(classOf<ClearConversationContent>())

        @Mock
        val clientRepository: ClientRepository = mock(classOf<ClientRepository>())

        @Mock
        val userRepository: UserRepository = mock(classOf<UserRepository>())

        @Mock
        val messageSender: MessageSender = mock(classOf<MessageSender>())

        fun withClearConversationContent(isSuccessFull: Boolean): Arrangement {
            given(clearConversationContent)
                .suspendFunction(clearConversationContent::invoke)
                .whenInvokedWith(anything())
                .thenReturn(if (isSuccessFull) Either.Right(Unit) else Either.Left(CoreFailure.Unknown(Throwable("an error"))))

            return this
        }

        fun withCurrentClientId(isSuccessFull: Boolean): Arrangement {
            given(clientRepository)
                .suspendFunction(clientRepository::currentClientId)
                .whenInvoked()
                .thenReturn(if (isSuccessFull) Either.Right(ClientId("someValue")) else Either.Left(CoreFailure.Unknown(Throwable("an error"))))

            return this
        }

        fun withGetSelfConversationId(): Arrangement {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getSelfConversationId)
                .whenInvoked()
                .thenReturn(ConversationId("someValue", "someDomain"))

            return this
        }

        fun withGetSelfUserId(): Arrangement {
            given(userRepository)
                .function(userRepository::getSelfUserId)
                .whenInvoked()
                .thenReturn(UserId("someValue", "someDomain"))

            return this
        }

        fun withMessageSending(isSuccessFull: Boolean): Arrangement {
            given(messageSender)
                .suspendFunction(messageSender::sendMessage)
                .whenInvokedWith(anything())
                .thenReturn(if (isSuccessFull) Either.Right(Unit) else Either.Left(CoreFailure.Unknown(Throwable("an error"))))

            return this
        }

        fun arrange() = this to ClearConversationContentUseCaseImpl(
            clearConversationContent,
            clientRepository,
            conversationRepository,
            userRepository,
            messageSender
        )
    }

}
