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
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.Either
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.mock

internal interface MLSConversationRepositoryArrangement {
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

internal class MLSConversationRepositoryArrangementImpl : MLSConversationRepositoryArrangement {
    override val mlsConversationRepository = mock<MLSConversationRepository>(mode = MockMode.autoUnit)

    override suspend fun withIsGroupOutOfSync(result: Either<CoreFailure, Boolean>) {
        everySuspend {
            mlsConversationRepository.isLocalGroupEpochStale(any(), any(), any())
        }.returns(result)
    }

    override suspend fun withUserIdentity(result: Either<CoreFailure, List<WireIdentity>>) {
        everySuspend {
            mlsConversationRepository.getUserIdentity(any(), any())
        }.returns(result)
    }

    override suspend fun withMembersIdentities(result: Either<CoreFailure, Map<UserId, List<WireIdentity>>>) {
        everySuspend {
            mlsConversationRepository.getMembersIdentities(any(), any(), any())
        }.returns(result)
    }

    override suspend fun withJoinGroupByExternalCommit(groupId: GroupID, groupInfo: ByteArray, result: Either<CoreFailure, Unit>) = apply {
        everySuspend {
            mlsConversationRepository.joinGroupByExternalCommit(any(), eq(groupId), eq(groupInfo))
        }.returns(result)
    }
}
