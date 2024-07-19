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
package com.wire.kalium.logic.feature.analytics

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.util.arrangement.MessageSenderArrangement
import com.wire.kalium.logic.util.arrangement.MessageSenderArrangementImpl
import com.wire.kalium.logic.util.arrangement.SelfConversationIdProviderArrangement
import com.wire.kalium.logic.util.arrangement.SelfConversationIdProviderArrangementImpl
import com.wire.kalium.logic.util.arrangement.provider.CurrentClientIdProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CurrentClientIdProviderArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangementImpl
import io.mockative.any
import io.mockative.coVerify
import io.mockative.matches
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class AnalyticsIdentifierManagerTest {

    @Test
    fun givenAnalyticsMigrationIsComplete_whenDeletingPreviousTrackingIdentifier_thenUserConfigRepositoryIsCalled() = runTest {
        // given
        val (arrangement, manager) = Arrangement().arrange {
            withDeletePreviousTrackingIdentifier()
        }

        // when
        manager.onMigrationComplete()

        // then
        coVerify {
            arrangement.userConfigRepository.deletePreviousTrackingIdentifier()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAnIdentifier_whenPropagatingTrackingIdentifier_thenSignalingMessageIsSent() = runTest {
        // given
        val (arrangement, manager) = Arrangement().arrange {
            withCurrentClientIdSuccess(SELF_CLIENT_ID)
            withSelfConversationIds(listOf(SELF_CONVERSATION_ID))
            withSendMessageSucceed()
        }

        // when
        manager.propagateTrackingIdentifier(CURRENT_IDENTIFIER)

        // then
        coVerify {
            arrangement.messageSender.sendMessage(matches {
                it is Message.Signaling && it.content is MessageContent.DataTransfer
            }, any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAnIdentifierAndNoClientId_whenPropagatingTrackingIdentifier_thenMessageIsNotSent() = runTest {
        // given
        val (arrangement, manager) = Arrangement().arrange {
            withCurrentClientIdFailure(StorageFailure.DataNotFound)
        }

        // when
        manager.propagateTrackingIdentifier(CURRENT_IDENTIFIER)

        // then
        coVerify {
            arrangement.messageSender.sendMessage(any(), any())
        }.wasNotInvoked()
    }

    private companion object {
        val SELF_USER_ID = UserId("user_id", "user_domain")
        val SELF_CLIENT_ID = ClientId("client_id")
        val SELF_CONVERSATION_ID = ConversationId("conversation_id", "conversation_domain")
        const val CURRENT_IDENTIFIER = "abcd-1234"
    }

    private class Arrangement : UserConfigRepositoryArrangement by UserConfigRepositoryArrangementImpl(),
        MessageSenderArrangement by MessageSenderArrangementImpl(),
        CurrentClientIdProviderArrangement by CurrentClientIdProviderArrangementImpl(),
        SelfConversationIdProviderArrangement by SelfConversationIdProviderArrangementImpl() {

        private val useCase: AnalyticsIdentifierManager = AnalyticsIdentifierManager(
            messageSender = messageSender,
            userConfigRepository = userConfigRepository,
            selfUserId = SELF_USER_ID,
            selfClientIdProvider = currentClientIdProvider,
            selfConversationIdProvider = selfConversationIdProvider
        )

        fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, AnalyticsIdentifierManager> {
            runBlocking { block() }
            return this to useCase
        }
    }
}
