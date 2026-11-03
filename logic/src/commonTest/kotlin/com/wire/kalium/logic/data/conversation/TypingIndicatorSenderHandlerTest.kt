/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TypingIndicatorSenderHandlerTest {

    @Test
    fun givenARegularConversation_whenStartedEventIsRequested_thenStartedStatusIsSent() = runTest {
        val conversation = TestConversation.CONVERSATION
        val conversationRepository = mock<ConversationRepository>()
        everySuspend { conversationRepository.getConversationById(any()) } returns Either.Right(conversation)
        everySuspend { conversationRepository.sendTypingIndicatorStatus(any(), any()) } returns Either.Right(Unit)
        val handler = TypingIndicatorSenderHandlerImpl(conversationRepository, testKaliumDispatcher, this)

        handler.sendStartedAndEnqueueStoppingEvent(conversation.id)
        runCurrent()

        verifySuspend(VerifyMode.exactly(1)) {
            conversationRepository.sendTypingIndicatorStatus(conversation.id, Conversation.TypingIndicatorMode.STARTED)
        }
    }

    @Test
    fun givenTheSelfConversation_whenStartedEventIsRequested_thenNothingIsSent() = runTest {
        val selfConversation = TestConversation.SELF(TestConversation.MLS_PROTOCOL_INFO)
        val conversationRepository = mock<ConversationRepository>()
        everySuspend { conversationRepository.getConversationById(any()) } returns Either.Right(selfConversation)
        everySuspend { conversationRepository.sendTypingIndicatorStatus(any(), any()) } returns Either.Right(Unit)
        val handler = TypingIndicatorSenderHandlerImpl(conversationRepository, testKaliumDispatcher, this)

        handler.sendStartedAndEnqueueStoppingEvent(selfConversation.id)
        runCurrent()

        verifySuspend(VerifyMode.not) { conversationRepository.sendTypingIndicatorStatus(any(), any()) }
    }
}
