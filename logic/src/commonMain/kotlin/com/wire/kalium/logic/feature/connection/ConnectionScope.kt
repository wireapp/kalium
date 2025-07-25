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

package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver

@Suppress("LongParameterList")
class ConnectionScope internal constructor(
    private val connectionRepository: ConnectionRepository,
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val oneOnOneResolver: OneOnOneResolver,
    private val newGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator,
    private val fetchConversationUseCase: FetchConversationUseCase,
    private val transactionProvider: CryptoTransactionProvider
) {
    val sendConnectionRequest: SendConnectionRequestUseCase
        get() = SendConnectionRequestUseCaseImpl(
            connectionRepository,
            userRepository,
            transactionProvider
        )

    val acceptConnectionRequest: AcceptConnectionRequestUseCase
        get() = AcceptConnectionRequestUseCaseImpl(
            connectionRepository,
            conversationRepository,
            oneOnOneResolver,
            newGroupConversationSystemMessagesCreator,
            fetchConversationUseCase,
            transactionProvider
        )

    val cancelConnectionRequest: CancelConnectionRequestUseCase
        get() = CancelConnectionRequestUseCaseImpl(
            connectionRepository,
            transactionProvider
        )

    val ignoreConnectionRequest: IgnoreConnectionRequestUseCase
        get() = IgnoreConnectionRequestUseCaseImpl(
            connectionRepository,
            transactionProvider
        )

    val markConnectionRequestAsNotified: MarkConnectionRequestAsNotifiedUseCase
        get() = MarkConnectionRequestAsNotifiedUseCaseImpl(connectionRepository)

    val blockUser: BlockUserUseCase
        get() = BlockUserUseCaseImpl(connectionRepository, transactionProvider)

    val unblockUser: UnblockUserUseCase
        get() = UnblockUserUseCaseImpl(connectionRepository, transactionProvider)

}
