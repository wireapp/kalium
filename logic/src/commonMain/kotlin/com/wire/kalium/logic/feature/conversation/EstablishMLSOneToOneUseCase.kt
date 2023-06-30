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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi

interface EstablishMLSOneToOneUseCase {
    suspend operator fun invoke(userId: UserId): Either<CoreFailure, ConversationId>
}

class EstablishMLSOneToOneUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val joinOrEstablishMLSGroup: JoinExistingMLSConversationUseCase
): EstablishMLSOneToOneUseCase {
    override suspend fun invoke(userId: UserId): Either<CoreFailure, ConversationId> =
        conversationRepository.getConversationIds(Conversation.Type.ONE_ON_ONE, Conversation.Protocol.MLS)
            .flatMap { conversationIds ->
                if (conversationIds.isEmpty()) {
                    conversationRepository.fetchMlsOneToOneConversation(userId)
                        .flatMap { conversation ->
                            joinOrEstablishMLSGroup(conversation.id)
                                .map {
                                    conversation.id
                                }
                        }
                } else {
                    Either.Right(conversationIds.first())
                }
            }


        // does mls 1-1 conversation exist? If not fetch
        // is the mls group established? If not establish
//     }


//     private suspend fun joinOrEstablishMLSGroup(conversation: Conversation, otherUserId: UserId): Either<CoreFailure, Unit> {
//         return if (conversation.protocol is Conversation.ProtocolInfo.MLSCapable) {
//             if (conversation.protocol.epoch == 0UL) {
//                 kaliumLogger.i("Establish group for ${conversation.type}")
//                 conversationRepository.getConversationMembers(conversation.id).flatMap { members ->
//                     mlsConversationRepository.establishMLSGroup(
//                         conversation.protocol.groupId,
//                         listOf(members.first())
//                     )
//                 }
//             } else {
//                 wrapApiRequest {
//                     conversationApi.fetchGroupInfo(conversation.id.toApi())
//                 }.flatMap { groupInfo ->
//                     mlsConversationRepository.joinGroupByExternalCommit(
//                         conversation.protocol.groupId,
//                         groupInfo
//                     )
//                 }
//             }
//         } else {
//             Either.Right(Unit)
//         }
//     }

}
