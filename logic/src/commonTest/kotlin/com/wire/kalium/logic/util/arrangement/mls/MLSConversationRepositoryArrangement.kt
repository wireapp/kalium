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
package com.wire.kalium.logic.util.arrangement.mls

import com.wire.kalium.cryptography.WireIdentity
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.Either
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock

interface MLSConversationRepositoryArrangement {
    val mlsConversationRepository: MLSConversationRepository

    suspend fun withIsGroupOutOfSync(result: Either<CoreFailure, Boolean>)
    suspend fun withUserIdentity(result: Either<CoreFailure, List<WireIdentity>>)
    suspend fun withMembersIdentities(result: Either<CoreFailure, Map<UserId, List<WireIdentity>>>)
    suspend fun withJoinGroupByExternalCommit(
        groupId: GroupID,
        groupInfo: ByteArray,
        result: Either<CoreFailure, Unit>
    ): MLSConversationRepositoryArrangementImpl
}

class MLSConversationRepositoryArrangementImpl : MLSConversationRepositoryArrangement {
    override val mlsConversationRepository = mock(MLSConversationRepository::class)

    override suspend fun withIsGroupOutOfSync(result: Either<CoreFailure, Boolean>) {
        coEvery {
            mlsConversationRepository.isGroupOutOfSync(any(), any())
        }.returns(result)
    }

    override suspend fun withUserIdentity(result: Either<CoreFailure, List<WireIdentity>>) {
        coEvery {
            mlsConversationRepository.getUserIdentity(any())
        }.returns(result)
    }

    override suspend fun withMembersIdentities(result: Either<CoreFailure, Map<UserId, List<WireIdentity>>>) {
        coEvery {
            mlsConversationRepository.getMembersIdentities(any(), any())
        }.returns(result)
    }

    override suspend fun withJoinGroupByExternalCommit(groupId: GroupID, groupInfo: ByteArray, result: Either<CoreFailure, Unit>) = apply {
        coEvery {
            mlsConversationRepository.joinGroupByExternalCommit(groupId, groupInfo)
        }.returns(result)
    }
}
