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

package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides

@BindingContainer
internal object ConnectionUseCaseBindings {
    @Provides
    fun provideSendConnectionRequest(
        connectionRepository: ConnectionRepository,
        userRepository: UserRepository,
        transactionProvider: CryptoTransactionProvider,
    ): SendConnectionRequestUseCase =
        SendConnectionRequestUseCaseImpl(connectionRepository, userRepository, transactionProvider)

    @Provides
    fun provideAcceptConnectionRequest(
        connectionRepository: ConnectionRepository,
        conversationRepository: ConversationRepository,
        oneOnOneResolver: OneOnOneResolver,
        newGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator,
        fetchConversationUseCase: FetchConversationUseCase,
        transactionProvider: CryptoTransactionProvider,
    ): AcceptConnectionRequestUseCase = AcceptConnectionRequestUseCaseImpl(
        connectionRepository,
        conversationRepository,
        oneOnOneResolver,
        newGroupConversationSystemMessagesCreator,
        fetchConversationUseCase,
        transactionProvider,
    )

    @Provides
    fun provideCancelConnectionRequest(
        connectionRepository: ConnectionRepository,
        transactionProvider: CryptoTransactionProvider,
    ): CancelConnectionRequestUseCase = CancelConnectionRequestUseCaseImpl(connectionRepository, transactionProvider)

    @Provides
    fun provideIgnoreConnectionRequest(
        connectionRepository: ConnectionRepository,
        transactionProvider: CryptoTransactionProvider,
    ): IgnoreConnectionRequestUseCase = IgnoreConnectionRequestUseCaseImpl(connectionRepository, transactionProvider)

    @Provides
    fun provideBlockUser(
        connectionRepository: ConnectionRepository,
        transactionProvider: CryptoTransactionProvider,
    ): BlockUserUseCase = BlockUserUseCaseImpl(connectionRepository, transactionProvider)

    @Provides
    fun provideUnblockUser(
        connectionRepository: ConnectionRepository,
        transactionProvider: CryptoTransactionProvider,
    ): UnblockUserUseCase = UnblockUserUseCaseImpl(connectionRepository, transactionProvider)
}
