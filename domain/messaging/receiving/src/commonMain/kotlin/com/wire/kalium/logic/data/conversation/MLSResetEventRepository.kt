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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo.MLSCapable.GroupState
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.mls.ConversationProtocolGetter
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import kotlinx.coroutines.flow.Flow

/** MLS operations used only while applying an incoming conversation reset event. */
public interface MLSResetEventRepository {
    public suspend fun leaveGroup(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID,
    ): Either<CoreFailure, Unit>

    public suspend fun hasEstablishedMLSGroup(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID,
    ): Either<MLSFailure, Boolean>

    public suspend fun updateGroupIdAndState(
        conversationId: ConversationId,
        newGroupId: GroupID,
        newEpoch: Long,
        groupState: ConversationEntity.GroupState = ConversationEntity.GroupState.PENDING_JOIN,
    ): Either<CoreFailure, Unit>
}

public interface MLSWelcomeEventRepository : ConversationProtocolGetter {
    public suspend fun updateConversationGroupState(groupID: GroupID, groupState: GroupState): Either<StorageFailure, Unit>

    public suspend fun observeConversationDetailsById(
        conversationID: ConversationId
    ): Flow<Either<StorageFailure, ConversationDetails>>
}
