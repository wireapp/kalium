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
package com.wire.kalium.logic.feature.conversation.mls

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.toModel
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapMLSRequest

interface ConversationVerificationStatusChecker {
    // TODO: remove not used
    suspend fun check(groupID: GroupID): Either<CoreFailure, Conversation.VerificationStatus>
}

internal class ConversationVerificationStatusCheckerImpl(
    private val mlsClientProvider: MLSClientProvider,
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : ConversationVerificationStatusChecker {
    override suspend fun check(groupID: GroupID): Either<CoreFailure, Conversation.VerificationStatus> =
        mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            wrapMLSRequest { mlsClient.isGroupVerified(idMapper.toCryptoModel(groupID)) }
        }.map { it.toModel() }
}
