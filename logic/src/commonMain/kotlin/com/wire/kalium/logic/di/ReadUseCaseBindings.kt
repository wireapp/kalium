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
import com.wire.kalium.logic.feature.conversation.ObserveConversationDetailsUseCase
import com.wire.kalium.logic.feature.message.GetMessageByIdUseCase
import com.wire.kalium.logic.feature.message.ObserveMessageByIdUseCase
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory

// Keep the public use case constructors and their default dispatcher ABI unchanged.
// These are deliberately unscoped: only the selected repositories are consumer caches.
internal interface MessageReadBindings {
    val observeMessageById: ObserveMessageByIdUseCase
    val getMessageById: GetMessageByIdUseCase

    @Provides
    fun provideObserveMessageById(repository: MessageRepository, dispatchers: KaliumDispatcher): ObserveMessageByIdUseCase =
        ObserveMessageByIdUseCase(repository, dispatchers)

    @Provides
    fun provideGetMessageById(repository: MessageRepository, dispatchers: KaliumDispatcher): GetMessageByIdUseCase =
        GetMessageByIdUseCase(repository, dispatchers)
}

internal interface ConversationReadBindings {
    val observeConversationDetails: ObserveConversationDetailsUseCase

    @Provides
    fun provideObserveConversationDetails(repository: ConversationRepository, dispatchers: KaliumDispatcher): ObserveConversationDetailsUseCase =
        ObserveConversationDetailsUseCase(repository, dispatchers)
}

// These stateless graphs have no contributions to aggregate. Metro 1.4.5 supports final
// common graphs (its commonTest CycleBreakingTests exercises graph factories on KMP).
// A facade supplies its already captured repository, never a fresh consumer or owner adapter.
@DependencyGraph
internal interface CapturedMessageReadGraph : MessageReadBindings {
    @DependencyGraph.Factory
    interface Factory {
        fun create(
            @Provides repository: MessageRepository,
            @Provides dispatchers: KaliumDispatcher,
        ): CapturedMessageReadGraph
    }
}

@DependencyGraph
internal interface CapturedConversationReadGraph : ConversationReadBindings {
    @DependencyGraph.Factory
    interface Factory {
        fun create(
            @Provides repository: ConversationRepository,
            @Provides dispatchers: KaliumDispatcher,
        ): CapturedConversationReadGraph
    }
}

internal fun messageReads(repository: MessageRepository): CapturedMessageReadGraph =
    createGraphFactory<CapturedMessageReadGraph.Factory>().create(repository, KaliumDispatcherImpl)

internal fun conversationReads(repository: ConversationRepository): CapturedConversationReadGraph =
    createGraphFactory<CapturedConversationReadGraph.Factory>().create(repository, KaliumDispatcherImpl)
