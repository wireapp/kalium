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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.di.UserSessionLifetime
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

internal interface ConversationEntryPoints {
    val conversationRepository: ConversationRepository
    val getConversations: GetConversationsUseCase
    val getConversationDetails: GetConversationUseCase
    val getOneToOneConversation: GetOneToOneConversationDetailsUseCase
    val observeConversationListDetails: ObserveConversationListDetailsUseCase
    val observeConversationDetails: ObserveConversationDetailsUseCase
    val getConversationProtocolInfo: GetConversationProtocolInfoUseCase
}

@BindingContainer
internal object ConversationUseCaseBindings {

    @Provides
    @SingleIn(UserSessionLifetime::class)
    fun provideConversationRepository(factory: ConversationRepositoryFactory): ConversationRepository = factory()

    @Provides
    fun provideGetConversations(repository: ConversationRepository): GetConversationsUseCase =
        GetConversationsUseCase(repository)

    @Provides
    fun provideGetConversationDetails(repository: ConversationRepository): GetConversationUseCase =
        GetConversationUseCase(repository)

    @Provides
    fun provideGetOneToOneConversation(repository: ConversationRepository): GetOneToOneConversationDetailsUseCase =
        GetOneToOneConversationDetailsUseCase(repository)

    @Provides
    fun provideObserveConversationListDetails(repository: ConversationRepository): ObserveConversationListDetailsUseCase =
        ObserveConversationListDetailsUseCaseImpl(repository)

    @Provides
    fun provideObserveConversationDetails(repository: ConversationRepository): ObserveConversationDetailsUseCase =
        ObserveConversationDetailsUseCase(repository)

    @Provides
    fun provideGetConversationProtocolInfo(repository: ConversationRepository): GetConversationProtocolInfoUseCase =
        GetConversationProtocolInfoUseCase(repository)
}
