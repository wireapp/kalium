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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.messaging.sending.MessageTarget
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class AnalyticsIdentifierManagerTest {

    @Test
    fun givenAnalyticsMigrationIsComplete_whenDeletingPreviousTrackingIdentifier_thenUserConfigRepositoryIsCalled() = runTest {
        // given
        val (arrangement, manager) = Arrangement().arrange {
            withWaitUntilLiveSuccessful()
            withDeletePreviousTrackingIdentifier()
        }

        // when
        manager.onMigrationComplete()

        // then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.deletePreviousTrackingIdentifier()
        }
    }

    @Test
    fun givenAnIdentifier_whenPropagatingTrackingIdentifier_thenSignalingMessageIsSent() = runTest {
        // given
        val (arrangement, manager) = Arrangement().arrange {
            withWaitUntilLiveSuccessful()
            withCurrentClientIdSuccess(SELF_CLIENT_ID)
            withSelfConversationIds(listOf(SELF_CONVERSATION_ID))
            withSendMessageSucceed()
        }

        // when
        manager.propagateTrackingIdentifier(CURRENT_IDENTIFIER)

        // then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(matches {
                it is Message.Signaling && it.content is MessageContent.DataTransfer
            }, any())
        }
    }

    @Test
    fun givenAnIdentifierAndNoClientId_whenPropagatingTrackingIdentifier_thenMessageIsNotSent() = runTest {
        // given
        val (arrangement, manager) = Arrangement().arrange {
            withWaitUntilLiveSuccessful()
            withCurrentClientIdFailure(StorageFailure.DataNotFound)
        }

        // when
        manager.propagateTrackingIdentifier(CURRENT_IDENTIFIER)

        // then
        verifySuspend(VerifyMode.not) {
            arrangement.messageSender.sendMessage(any(), any())
        }
    }

    private companion object {
        val SELF_USER_ID = UserId("user_id", "user_domain")
        val SELF_CLIENT_ID = ClientId("client_id")
        val SELF_CONVERSATION_ID = ConversationId("conversation_id", "conversation_domain")
        const val CURRENT_IDENTIFIER = "abcd-1234"
    }

    private class Arrangement {
        val userConfigRepository = mock<UserConfigRepository>(mode = MockMode.autoUnit)
        val messageSender = mock<MessageSender>(mode = MockMode.autoUnit)
        val currentClientIdProvider = mock<CurrentClientIdProvider>(mode = MockMode.autoUnit)
        val selfConversationIdProvider = mock<SelfConversationIdProvider>(mode = MockMode.autoUnit)
        val syncManager = mock<SyncManager>(mode = MockMode.autoUnit)

        private val useCase: AnalyticsIdentifierManager = AnalyticsIdentifierManager(
            messageSender = messageSender,
            userConfigRepository = userConfigRepository,
            selfUserId = SELF_USER_ID,
            selfClientIdProvider = currentClientIdProvider,
            selfConversationIdProvider = selfConversationIdProvider,
            syncManager = syncManager
        )

        suspend fun withWaitUntilLiveSuccessful() = apply {
            everySuspend {
                syncManager.waitUntilLiveOrFailure()
            } returns Either.Right(Unit)
        }

        suspend fun withDeletePreviousTrackingIdentifier() = apply {
            everySuspend {
                userConfigRepository.deletePreviousTrackingIdentifier()
            } returns Unit
        }

        suspend fun withCurrentClientIdSuccess(currentClientId: ClientId) = apply {
            everySuspend {
                currentClientIdProvider.invoke()
            } returns Either.Right(currentClientId)
        }

        suspend fun withCurrentClientIdFailure(error: StorageFailure) = apply {
            everySuspend {
                currentClientIdProvider.invoke()
            } returns Either.Left(error)
        }

        suspend fun withSelfConversationIds(conversationIds: List<ConversationId>) = apply {
            everySuspend {
                selfConversationIdProvider.invoke()
            } returns Either.Right(conversationIds)
        }

        suspend fun withSendMessageSucceed() = apply {
            everySuspend {
                messageSender.sendMessage(any(), any<MessageTarget>())
            } returns Either.Right(Unit)
        }

        fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, AnalyticsIdentifierManager> {
            runBlocking { block() }
            return this to useCase
        }
    }
}
