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
package com.wire.kalium.logic.feature.debug

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.util.DebugKaliumApi

/**
 * Returns the current conv epoch from core-crypto
 * see [MlsCoreCryptoContext.conversationEpoch]
 */
@DebugKaliumApi("Debug-only API for inspecting conversation epoch in core crypto.")
public interface GetConversationEpochFromCCUseCase {
    public suspend operator fun invoke(conversationId: ConversationId): GetConversationEpochFromCCResult
}

internal class GetConversationEpochFromCCUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val transactionProvider: CryptoTransactionProvider,
) : GetConversationEpochFromCCUseCase {

    override suspend fun invoke(conversationId: ConversationId): GetConversationEpochFromCCResult =
        conversationRepository.getConversationById(conversationId).fold(
            fnL = { GetConversationEpochFromCCResult.Failure.Generic(it) },
            fnR = { conversation ->
                val protocol = conversation.protocol as? Conversation.ProtocolInfo.MLSCapable
                if (protocol == null) {
                    GetConversationEpochFromCCResult.Failure.NotMlsConversation
                } else {
                    transactionProvider.mlsTransaction("GetConversationEpochFromCC") { mlsContext ->
                        wrapMLSRequest { mlsContext.conversationEpoch(protocol.groupId.value) }
                    }.fold(
                        fnL = { GetConversationEpochFromCCResult.Failure.Generic(it) },
                        fnR = { GetConversationEpochFromCCResult.Success(it) }
                    )
                }
            }
        )
}

@DebugKaliumApi("Debug-only result for core crypto conversation epoch inspection.")
public sealed class GetConversationEpochFromCCResult {
    @DebugKaliumApi("Debug-only success result for core crypto conversation epoch inspection.")
    public data class Success(val epoch: ULong) : GetConversationEpochFromCCResult()

    @DebugKaliumApi("Debug-only failure result for core crypto conversation epoch inspection.")
    public sealed class Failure : GetConversationEpochFromCCResult() {
        @DebugKaliumApi("Debug-only not-MLS failure for core crypto conversation epoch inspection.")
        public data object NotMlsConversation : Failure()

        @DebugKaliumApi("Debug-only generic failure for core crypto conversation epoch inspection.")
        public data class Generic(val coreFailure: CoreFailure) : Failure()
    }
}
