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

import com.wire.kalium.logic.data.conversation.ConversationDataSource
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.MessageDataSource
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.paging.NomadMessagePagingCoordinator
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.userstorage.di.UserStorage

/**
 * One recipe per selected datasource, shared by legacy factory getters and consumer bindings.
 * The session still owns resources and chooses each paging coordinator's factory/capture lifetime.
 * No repository is retained here and no resource is created, cancelled or closed by this factory.
 */
internal class ReadRepositoryFactory(
    private val userId: UserId,
    private val userStorage: UserStorage,
    private val network: AuthenticatedNetworkContainer,
) {
    fun message(pagingCoordinator: () -> NomadMessagePagingCoordinator?): MessageRepository = MessageDataSource(
        messageApi = network.messageApi,
        mlsMessageApi = network.mlsMessageApi,
        messageDAO = userStorage.database.messageDAO,
        selfUserId = userId,
        nomadMessagePagingCoordinator = pagingCoordinator(),
    )

    fun conversation(): ConversationRepository = ConversationDataSource(
        userId,
        userStorage.database.conversationDAO,
        userStorage.database.memberDAO,
        network.conversationApi,
        userStorage.database.messageDAO,
        userStorage.database.messageDraftDAO,
        userStorage.database.clientDAO,
        network.clientApi,
        userStorage.database.conversationMetaDataDAO,
    )
}
