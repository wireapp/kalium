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

package com.wire.kalium.logic.di

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** Deferred construction over existing user resources; neither callback may resolve a feature scope. */
internal class ReadRepositories(
    val messages: () -> MessageRepository,
    val conversations: () -> ConversationRepository,
    val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
)

/** Retained lazily by the session. Final platform roots aggregate any platform contributions. */
internal interface KaliumReadGraph {
    val consumerGraphFactory: KaliumConsumerGraph.Factory
}

internal expect fun createKaliumReadGraph(repositories: ReadRepositories): KaliumReadGraph

@GraphExtension(ConsumerLifetime::class)
internal interface KaliumConsumerGraph : MessageReadBindings, ConversationReadBindings {
    @Provides
    @SingleIn(ConsumerLifetime::class)
    fun provideMessageRepository(repositories: ReadRepositories, work: ConsumerWork): MessageRepository =
        ConsumerMessageRepository(repositories.messages(), work)

    @Provides
    @SingleIn(ConsumerLifetime::class)
    fun provideConversationRepository(repositories: ReadRepositories, work: ConsumerWork): ConversationRepository =
        ConsumerConversationRepository(repositories.conversations(), work)

    @Provides
    fun provideDispatchers(repositories: ReadRepositories): KaliumDispatcher = repositories.dispatchers

    @GraphExtension.Factory
    interface Factory {
        fun create(@Provides work: ConsumerWork): KaliumConsumerGraph
    }
}
