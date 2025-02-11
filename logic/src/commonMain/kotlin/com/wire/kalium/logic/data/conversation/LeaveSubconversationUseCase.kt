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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapMLSRequest
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.authenticated.conversation.SubconversationMemberDTO

/**
 * Leave a sub-conversation you've previously joined
 */
internal interface LeaveSubconversationUseCase {
    suspend operator fun invoke(conversationId: ConversationId, subconversationId: SubconversationId): Either<CoreFailure, Unit>
}

internal class LeaveSubconversationUseCaseImpl(
    val conversationApi: ConversationApi,
    val mlsClientProvider: MLSClientProvider,
    val subconversationRepository: SubconversationRepository,
    val selfUserId: UserId,
    val selfClientIdProvider: CurrentClientIdProvider
) : LeaveSubconversationUseCase {
    override suspend fun invoke(conversationId: ConversationId, subconversationId: SubconversationId): Either<CoreFailure, Unit> =
        retrieveSubconversationGroupId(conversationId, subconversationId).flatMap { groupId ->
            groupId?.let { groupId ->
                wrapApiRequest {
                    conversationApi.leaveSubconversation(conversationId.toApi(), subconversationId.toApi())
                }.flatMap {
                    subconversationRepository.deleteSubconversation(conversationId, subconversationId)
                    mlsClientProvider.getMLSClient().flatMap { mlsClient ->
                        wrapMLSRequest {
                            mlsClient.wipeConversation(groupId.toCrypto())
                        }
                    }
                }
            } ?: Either.Right(Unit)
        }

    suspend fun retrieveSubconversationGroupId(
        conversationId: ConversationId,
        subconversationId: SubconversationId
    ): Either<CoreFailure, GroupID?> =
        selfClientIdProvider().flatMap { selfClientId ->
            subconversationRepository.getSubconversationInfo(conversationId, subconversationId)?.let {
                Either.Right(it)
            } ?: wrapApiRequest { conversationApi.fetchSubconversationDetails(conversationId.toApi(), subconversationId.toApi()) }.flatMap {
                if (it.members.contains(SubconversationMemberDTO(selfClientId.value, selfUserId.value, selfUserId.domain))) {
                    Either.Right(GroupID(it.groupId))
                } else {
                    Either.Right(null)
                }
            }
        }
}
