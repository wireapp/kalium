/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.call.CallModerationAction
import com.wire.kalium.logic.data.call.CallModerationActionsRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.ShouldRemoteMuteChecker
import com.wire.kalium.logic.feature.call.usecase.MuteCallUseCase
import com.wire.kalium.logic.framework.TestCall
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test

class CallingMessageHandlerTest {

    @Test
    fun givenCallingMessage_whenHandling_thenHandleInCallManager_andDoNotCallMute() = runTest {
        // given
        val content = CALLING_CONTENT
        val message = CALLING_MESSAGE.copy(content = content)
        val (arrangement, callingMessageHandler) = Arrangement().arrange()
        // when
        callingMessageHandler.handle(message, content)
        // then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.callManager.onCallingMessageReceived(message, content)
        }
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.muteCallUseCase.invoke(message.conversationId, any())
        }
    }

    @Test
    fun givenCallingMessage_whenHandling_andShouldRemoteMuteReturnsTrue_thenCallMute_butDoNotPassMessageToCallManager() = runTest {
        // given
        val content = REMOTE_MUTE_CONTENT
        val message = CALLING_MESSAGE.copy(content = content)
        val (arrangement, callingMessageHandler) = Arrangement()
            .withShouldRemoteMuteCheckerReturning(true)
            .arrange()
        // when
        callingMessageHandler.handle(message, content)
        // then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.muteCallUseCase.invoke(message.conversationId, true)
            arrangement.callModerationActionsRepository.addAction(
                message.conversationId, CallModerationAction(message.id, message.senderUserId, CallModerationAction.Type.MUTED)
            )
        }
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.callManager.onCallingMessageReceived(message, content)
        }
    }

    @Test
    fun givenCallingMessage_whenHandling_andShouldRemoteMuteReturnsFalse_thenDoNotCallMute_andDoNotPassMessageToCallManager() = runTest {
        // given
        val content = REMOTE_MUTE_CONTENT
        val message = CALLING_MESSAGE.copy(content = content)
        val (arrangement, callingMessageHandler) = Arrangement()
            .withShouldRemoteMuteCheckerReturning(false)
            .arrange()
        // when
        callingMessageHandler.handle(message, content)
        // then
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.muteCallUseCase.invoke(message.conversationId, true)
        }
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.callManager.onCallingMessageReceived(message, content)
        }
    }

    private inner class Arrangement {

        val currentClientIdProvider: CurrentClientIdProvider = mock(MockMode.autoUnit)
        val callManager: CallManager = mock(MockMode.autoUnit)
        val conversationRepository: ConversationRepository = mock(MockMode.autoUnit)
        val callModerationActionsRepository: CallModerationActionsRepository = mock(MockMode.autoUnit)
        val muteCallUseCase: MuteCallUseCase = mock(MockMode.autoUnit)
        val shouldRemoteMuteChecker: ShouldRemoteMuteChecker = mock(MockMode.autoUnit)

        fun withShouldRemoteMuteCheckerReturning(shouldRemoteMute: Boolean) = apply {
            everySuspend {
                shouldRemoteMuteChecker.check(any(), any(), any(), any(), any())
            } returns shouldRemoteMute
        }

        fun withCurrentClientIdProviderReturning(clientId: ClientId) = apply {
            everySuspend {
                currentClientIdProvider()
            } returns clientId.right()
        }

        fun withObserveConversationMembersReturning(members: List<Conversation.Member>) = apply {
            everySuspend {
                conversationRepository.observeConversationMembers(any())
            } returns flowOf(members)
        }

        init {
            withCurrentClientIdProviderReturning(TestClient.CLIENT_ID)
            withObserveConversationMembersReturning(emptyList())
        }

        fun arrange() = this to CallingMessageHandlerImpl(
            selfUserId = TestUser.SELF.id,
            currentClientIdProvider = currentClientIdProvider,
            callManager = lazy { callManager },
            conversationRepository = conversationRepository,
            callModerationActionsRepository = callModerationActionsRepository,
            muteCall = muteCallUseCase,
            shouldRemoteMuteChecker = shouldRemoteMuteChecker,
        )
    }

    companion object {
        val CALLING_CONTENT = MessageContent.Calling(
            value = """{"type":"TYPE","conversationId":"${TestCall.CONVERSATION_ID}"}""",
            conversationId = null
        )
        val REMOTE_MUTE_CONTENT = MessageContent.Calling(
            value = """{"type":"REMOTEMUTE","conversationId":"${TestCall.CONVERSATION_ID}"}""",
            conversationId = null
        )
        val CALLING_MESSAGE = Message.Signaling(
            id = "message-id",
            content = CALLING_CONTENT,
            conversationId = TestCall.CONVERSATION_ID,
            date = Clock.System.now(),
            senderUserId = TestUser.USER_ID,
            senderClientId = TestClient.CLIENT_ID,
            status = Message.Status.Read(0),
            isSelfMessage = false,
            expirationData = null,
        )
    }
}
