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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.client.ClientMapper
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.messaging.sending.ProteusRecipients

internal class ProteusRecipientsDataSource(
    private val userRepository: UserRepository,
    private val clientRepository: ClientRepository,
    private val clientRemoteRepository: ClientRemoteRepository,
    private val fetchConversationUseCase: FetchConversationUseCase,
    private val clientMapper: ClientMapper = MapperProvider.clientMapper(),
) : ProteusRecipients {

    override suspend fun removeClients(clients: Map<UserId, List<ClientId>>): Either<StorageFailure, List<UserId>> =
        clientRepository.removeClientsAndReturnUsersWithNoClients(clients)

    override suspend fun fetchUsers(userIds: Set<UserId>): Either<CoreFailure, Unit> =
        userRepository.fetchUsersByIds(userIds).map { }

    override suspend fun fetchConversation(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId
    ): Either<CoreFailure, Unit> = fetchConversationUseCase(transactionContext, conversationId)

    override suspend fun fetchClients(userIds: List<UserId>): Either<CoreFailure, Unit> =
        clientRemoteRepository.fetchOtherUserClients(userIds).flatMap { fetchedClients ->
            val insertClientParamList = fetchedClients.flatMap { (userId, clientList) ->
                clientMapper.toInsertClientParam(clientList, userId)
            }
            if (insertClientParamList.isEmpty()) Either.Right(Unit)
            else clientRepository.storeUserClientListAndRemoveRedundantClients(insertClientParamList)
        }

    override suspend fun markClientsAsValid(clients: Map<UserId, List<ClientId>>): Either<StorageFailure, Unit> =
        clientRepository.tryMarkClientsAsValid(clients)
}
