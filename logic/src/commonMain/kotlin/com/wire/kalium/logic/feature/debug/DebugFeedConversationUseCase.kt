/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly")

package com.wire.kalium.logic.feature.debug

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.persistence.db.feeders.MentionsFeeder
import com.wire.kalium.persistence.db.feeders.MessagesFeeder
import com.wire.kalium.persistence.db.feeders.ReactionsFeeder
import com.wire.kalium.persistence.db.feeders.UnreadEventsFeeder
import com.wire.kalium.util.DebugKaliumApi

/**
 * Debug-only use case that can feed a conversation with various synthetic datasets
 * (reactions, unread events, etc.) for performance and behaviour testing.
 *
 * This is intended strictly for developer tooling (hidden debug screens,
 * local profiling, QA scenarios).
 */
@DebugKaliumApi(
    message = "This use case is for debug / performance testing only and must not be used in production."
)
internal interface DebugFeedConversationUseCase {
    suspend operator fun invoke(
        conversationId: ConversationId,
        config: DebugFeedConfig = DebugFeedConfig.All
    ): DebugFeedResult
}

@DebugKaliumApi(
    message = "This use case is intended for debugging purposes only and should not be used in production code."
)
internal class DebugFeedConversationUseCaseImpl(
    private val messagesFeeder: MessagesFeeder,
    private val reactionsFeeder: ReactionsFeeder,
    private val unreadEventsFeeder: UnreadEventsFeeder,
    private val mentionsFeeder: MentionsFeeder,
) : DebugFeedConversationUseCase {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun invoke(
        conversationId: ConversationId,
        config: DebugFeedConfig
    ): DebugFeedResult = try {
        val daoId = conversationId.toDao()

        if (config.messages) {
            kaliumLogger.d("DebugFeed: feeding messages for $daoId")
            messagesFeeder.feed(daoId)
        }

        if (config.reactions) {
            kaliumLogger.d("DebugFeed: feeding reactions for $daoId")
            reactionsFeeder.feed(daoId)
        }

        if (config.unreadEvents) {
            kaliumLogger.d("DebugFeed: feeding unread events for $daoId")
            unreadEventsFeeder.feed(daoId)
        }

        if (config.mentions) {
            kaliumLogger.d("DebugFeed: feeding calls for $daoId")
            mentionsFeeder.feed(daoId)
        }

        kaliumLogger.d("DebugFeed: completed successfully for $daoId, config=$config")
        DebugFeedResult.Success
    } catch (e: Exception) {
        kaliumLogger.e("DebugFeed: failed for $conversationId with: ${e.stackTraceToString()}")
        DebugFeedResult.Failure(CoreFailure.Unknown(e))
    }
}

internal data class DebugFeedConfig(
    val messages: Boolean = false,
    val reactions: Boolean = false,
    val unreadEvents: Boolean = false,
    val mentions: Boolean = false
) {
    internal companion object {
        internal val All = DebugFeedConfig(
            messages = true,
            reactions = true,
            unreadEvents = true,
            mentions = true
        )
    }
}

internal sealed class DebugFeedResult {
    internal data object Success : DebugFeedResult()
    internal data class Failure(val coreFailure: CoreFailure) : DebugFeedResult()
}
