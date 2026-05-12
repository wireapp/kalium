/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.util.arrangement.repository

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.conversation.SubConversation
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.common.functional.Either
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.mock

internal interface SubconversationRepositoryArrangement {
    val subconversationRepository: SubconversationRepository

    suspend fun withInsertSubconversation(
        conversationId: ConversationId,
        subConversationId: SubconversationId,
        groupId: GroupID
    )

    suspend fun withGetSubconversationInfo(
        conversationId: ConversationId,
        subConversationId: SubconversationId,
        result: GroupID?
    )

    suspend fun withContainsSubconversation(groupId: GroupID, result: Boolean)

    suspend fun withDeleteSubconversation(conversationId: ConversationId, subConversationId: SubconversationId)

    suspend fun withDeleteRemoteSubConversation(
        conversationId: ConversationId,
        subConversationId: SubconversationId,
        result: Either<CoreFailure, Unit>
    )

    suspend fun withFetchRemoteSubConversationGroupInfo(
        conversationId: ConversationId,
        subConversationId: SubconversationId,
        result: Either<CoreFailure, ByteArray>
    )

    suspend fun withFetchRemoteSubConversationDetails(
        conversationId: ConversationId,
        subConversationId: SubconversationId,
        result: Either<NetworkFailure, SubConversation>
    )
}

internal class SubconversationRepositoryArrangementImpl : SubconversationRepositoryArrangement {

    override val subconversationRepository: SubconversationRepository = mock<SubconversationRepository>(mode = MockMode.autoUnit)

    override suspend fun withInsertSubconversation(
        conversationId: ConversationId,
        subConversationId: SubconversationId,
        groupId: GroupID
    ) {
        everySuspend {
            subconversationRepository.insertSubconversation(conversationId, subConversationId, groupId)
        }.returns(Unit)
    }

    override suspend fun withGetSubconversationInfo(
        conversationId: ConversationId,
        subConversationId: SubconversationId,
        result: GroupID?
    ) {
        everySuspend {
            subconversationRepository.getSubconversationInfo(conversationId, subConversationId)
        }.returns(result)
    }

    override suspend fun withContainsSubconversation(groupId: GroupID, result: Boolean) {
        everySuspend {
            subconversationRepository.containsSubconversation(groupId)
        }.returns(result)
    }

    override suspend fun withDeleteSubconversation(conversationId: ConversationId, subConversationId: SubconversationId) {
        everySuspend {
            subconversationRepository.deleteSubconversation(conversationId, subConversationId)
        }.returns(Unit)
    }

    override suspend fun withDeleteRemoteSubConversation(
        conversationId: ConversationId,
        subConversationId: SubconversationId,
        result: Either<CoreFailure, Unit>
    ) {
        everySuspend {
            subconversationRepository.deleteRemoteSubConversation(conversationId, subConversationId, any())
        }.returns(result)
    }

    override suspend fun withFetchRemoteSubConversationGroupInfo(
        conversationId: ConversationId,
        subConversationId: SubconversationId,
        result: Either<CoreFailure, ByteArray>
    ) {
        everySuspend {
            subconversationRepository.fetchRemoteSubConversationGroupInfo(conversationId, subConversationId)
        }.returns(result)
    }

    override suspend fun withFetchRemoteSubConversationDetails(
        conversationId: ConversationId,
        subConversationId: SubconversationId,
        result: Either<NetworkFailure, SubConversation>
    ) {
        everySuspend {
            subconversationRepository.fetchRemoteSubConversationDetails(conversationId, subConversationId)
        }.returns(result)
    }
}
