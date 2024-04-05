/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.logic.feature.scenario

import com.wire.kalium.calling.Calling
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.scenario.OnHttpRequest
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.data.message.MessageTarget
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.coEvery
import io.mockative.matches
import io.mockative.coVerify
import io.mockative.coEvery
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test

class OnHttpRequestTest {

    @Test
    fun givenSendInSelfConversationIsTrue_whenSending_messageIsSentInSelfConversations() = runTest(TestKaliumDispatcher.main) {
        val (arrangement, onHttpRequest) = Arrangement()
            .givenSelfConversationIdProviderReturns(Either.Right(listOf(Arrangement.selfConversationId)))
            .givenSendMessageSuccessful()
            .arrange()

        onHttpRequest.sendHandlerSuccess(
            context = null,
            messageString = "message",
            conversationId = Arrangement.conversationId,
            avsSelfUserId = Arrangement.selfUserId,
            avsSelfClientId = Arrangement.selfUserCLientId,
            messageTarget = MessageTarget.Conversation(),
            sendInSelfConversation = true
        )
        yield()

        coVerify {
            arrangement.messageSender.sendMessage(
                matches { it.conversationId == Arrangement.selfConversationId },
                matches { it is MessageTarget.Conversation },
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSendInSelfConversationIsFalse_whenSending_messageIsSentInTargetConversation() = runTest(TestKaliumDispatcher.main) {
        val (arrangement, onHttpRequest) = Arrangement()
            .givenSendMessageSuccessful()
            .arrange()

        onHttpRequest.sendHandlerSuccess(
            context = null,
            messageString = "message",
            conversationId = Arrangement.conversationId,
            avsSelfUserId = Arrangement.selfUserId,
            avsSelfClientId = Arrangement.selfUserCLientId,
            messageTarget = MessageTarget.Conversation(),
            sendInSelfConversation = false
        )
        yield()

        coVerify {
            arrangement.messageSender.sendMessage(
                matches { it.conversationId == Arrangement.conversationId },
                matches { it is MessageTarget.Conversation },
            )
        }.wasInvoked(exactly = once)
    }

    internal class Arrangement {

        @Mock
        val calling = mock(classOf<Calling>())

        @Mock
        val messageSender = mock(classOf<MessageSender>())

        @Mock
        val selfConversationIdProvider = mock(classOf<SelfConversationIdProvider>())

        fun arrange() = this to OnHttpRequest(
            CompletableDeferred(),
            calling,
            messageSender,
            CoroutineScope(TestKaliumDispatcher.main),
            selfConversationIdProvider
        )

        companion object {
            val conversationId = TestConversation.GROUP().id
            val selfConversationId = TestConversation.SELF().id
            val selfUserId = TestUser.SELF.id
            val selfUserCLientId = ClientId("self_client")
        }

        suspend fun givenSelfConversationIdProviderReturns(result: Either<StorageFailure, List<ConversationId>>) = apply {
            coEvery {
                selfConversationIdProvider.invoke()
            }.returns(result)
        }

        suspend fun givenSendMessageSuccessful() = apply {
            coEvery {
                messageSender.sendMessage(any(), any())
            }.returns(Either.Right(Unit))
        }
    }
}
