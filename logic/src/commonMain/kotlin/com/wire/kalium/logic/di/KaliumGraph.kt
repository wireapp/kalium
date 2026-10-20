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
import com.wire.kalium.logic.feature.conversation.LegacyConversationBindings
import com.wire.kalium.logic.feature.conversation.LegacyConversationDependencies
import com.wire.kalium.logic.feature.conversation.ObserveConversationDetailsUseCase
import com.wire.kalium.logic.feature.message.GetMessageByIdUseCase
import com.wire.kalium.logic.feature.message.LegacyMessageBindings
import com.wire.kalium.logic.feature.message.LegacyMessageDependencies
import com.wire.kalium.logic.feature.message.ObserveMessageByIdUseCase
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Factories over the prepared user's existing resource providers, never feature scopes or prebuilt use cases.
 * Each factory creates a repository wrapper; storage, network, crypto and mutable user state (including
 * the Nomad paging coordinator) retain their existing owners and provider cache policy.
 * Neither factory may retain the returned consumer repository in the parent.
 */
internal interface UserGraphDependencies {
    val dispatchers: KaliumDispatcher get() = KaliumDispatcherImpl
    fun createMessageRepository(): MessageRepository
    fun createConversationRepository(): ConversationRepository
}

/** Final annotated roots and their create intrinsics live in each platform's leaf source set. */
internal interface KaliumGraph {
    val userGraphFactory: KaliumUserGraph.Factory
}

internal expect fun createKaliumGraph(): KaliumGraph

/** Retained for the active account generation, including ordinary Apple background suspension. */
@GraphExtension(UserLifetime::class)
internal interface KaliumUserGraph : LegacyConversationBindings, LegacyMessageBindings {
    val consumerGraphFactory: KaliumConsumerGraph.Factory

    @GraphExtension.Factory
    interface Factory {
        fun create(
            @Provides dependencies: UserGraphDependencies,
            @Provides legacyConversationDependencies: LegacyConversationDependencies,
            @Provides legacyMessageDependencies: LegacyMessageDependencies,
        ): KaliumUserGraph
    }
}

/** Only these selected repository bindings are cached; all use case getters construct fresh values. */
@GraphExtension(ConsumerLifetime::class)
internal interface KaliumConsumerGraph {
    val observeConversationDetails: ObserveConversationDetailsUseCase
    val observeMessageById: ObserveMessageByIdUseCase
    val getMessageById: GetMessageByIdUseCase

    @Provides
    @SingleIn(ConsumerLifetime::class)
    fun provideMessageRepository(dependencies: UserGraphDependencies, work: ConsumerWork): MessageRepository =
        ConsumerMessageRepository(dependencies.createMessageRepository(), work)

    @Provides
    @SingleIn(ConsumerLifetime::class)
    fun provideConversationRepository(dependencies: UserGraphDependencies, work: ConsumerWork): ConversationRepository =
        ConsumerConversationRepository(dependencies.createConversationRepository(), work)

    @Provides
    fun provideObserveConversationDetails(
        repository: ConversationRepository,
        dependencies: UserGraphDependencies,
    ): ObserveConversationDetailsUseCase = ObserveConversationDetailsUseCase(repository, dependencies.dispatchers)

    @Provides
    fun provideObserveMessageById(
        repository: MessageRepository,
        dependencies: UserGraphDependencies,
    ): ObserveMessageByIdUseCase = ObserveMessageByIdUseCase(repository, dependencies.dispatchers)

    @Provides
    fun provideGetMessageById(
        repository: MessageRepository,
        dependencies: UserGraphDependencies,
    ): GetMessageByIdUseCase = GetMessageByIdUseCase(repository, dependencies.dispatchers)

    @GraphExtension.Factory
    interface Factory {
        fun create(@Provides work: ConsumerWork): KaliumConsumerGraph
    }
}
