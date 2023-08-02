/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.wrapStorageRequest
import kotlinx.coroutines.flow.first

internal interface UpdateConnectionProtocolUseCase {
    suspend operator fun invoke(connection: Connection): Either<CoreFailure, Unit>
}

internal class UpdateConnectionProtocolUseCaseImpl(
    private val connectionRepository: ConnectionRepository
): UpdateConnectionProtocolUseCase {

    override suspend fun invoke(connection: Connection): Either<CoreFailure, Unit> {

    }

    override suspend fun updateProtocolForConnection(connection: Connection): Either<CoreFailure, Unit> =
        commonProtocolsWithUser(connection.qualifiedToId)?.let { commonProtocols ->
            when {
                commonProtocols.contains(SupportedProtocol.MLS) -> {
                    connectionRepository.establishMlsOneToOne(connection.qualifiedToId).flatMap { conversationId ->
                        if (connection.qualifiedConversationId == conversationId) {
                            Either.Right(Unit)
                        } else {
                            wrapStorageRequest {
                                messageDAO.moveMessages(
                                    from = connection.qualifiedConversationId.toDao(),
                                    to = conversationId.toDao()
                                )
                            }.flatMap {
                                wrapStorageRequest {
                                    connectionDAO.updateConnectionConversation(
                                        conversationId =  conversationId.toDao(),
                                        userId = connection.qualifiedToId.toDao()
                                    )
                                }
                            }
                        }
                    }
                }

                commonProtocols.contains(SupportedProtocol.PROTEUS) -> {
                    // We don't support migrating from MLS to Proteus but we do allow
                    // going from readonly to proteus.
                    // TODO clear readonly flag for any existing 1-1 conversation
                    // conversationRepository.updateConversationReadonlyStatus(connection.qualifiedConversationId, false)
                    Either.Right(Unit)
                }

                else -> {
                    // TODO mark any existing 1-1 conversation as readonly
                    // conversationRepository.updateConversationReadonlyStatus(connection.qualifiedConversationId, true)
                    Either.Right(Unit)
                }
            }
        } ?: Either.Right(Unit)

    private suspend fun commonProtocolsWithUser(userId: UserId): Set<SupportedProtocol>? =
        userRepository.getKnownUser(userId).first()?.supportedProtocols?.let { otherProtocols ->
            userRepository.getSelfUser()?.supportedProtocols?.let { selfProtocols ->
                otherProtocols.intersect(selfProtocols)
            }
        }
}
