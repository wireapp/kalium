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
package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.EpochInfo
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import io.mockative.Mockable
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/**
 * This class is responsible for updating epoch info in a call.
 * Called when new epoch is requested or clients are requested in MLS calls to ensure AVS has the latest epoch info.
 */
@Mockable
internal fun interface EpochInfoUpdater {

    /**
     *  Fetches the latest epoch info for the given conversation and updates AVS if it's an MLS call
     */
    suspend operator fun invoke(conversationId: ConversationId)
}

internal class EpochInfoUpdaterImpl(
    private val callManager: Lazy<CallManager>,
    private val callRepository: CallRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : EpochInfoUpdater {
    override suspend fun invoke(conversationId: ConversationId) {
        callingLogger.d("[$TAG] -> ConversationId: ${conversationId.toLogString()}")
        val callMetadata = callRepository.getCallMetadata(conversationId)
        when {
            callMetadata == null -> callingLogger.d(
                    "[$TAG] -> No call metadata found for conversation, skipping epoch info update " +
                            "| ConversationId: ${conversationId.toLogString()}"
                )

            callMetadata.protocol !is Conversation.ProtocolInfo.MLS -> callingLogger.d(
                    "[$TAG] -> Call is not MLS, skipping epoch info update " +
                            "| ConversationId: ${conversationId.toLogString()} | Protocol: ${callMetadata.protocol}"
                )

            else -> callRepository.observeEpochInfo(conversationId)
                .flatMap { it.firstOrNull()?.right() ?: StorageFailure.DataNotFound.left() }
                .onFailure {
                    callingLogger.d(
                        "[$TAG] -> Failure when trying to get current epoch info " +
                                "| ConversationId: ${conversationId.toLogString()} | Error: $it"
                    )
                }
                .onSuccess { epochInfo: EpochInfo ->
                    callingLogger.d(
                        "[$TAG] -> Updating epoch info " +
                                "| ConversationId: ${conversationId.toLogString()} | Epoch info: $epochInfo"
                    )
                    withContext(dispatchers.default) {
                        callManager.value.updateEpochInfo(conversationId, epochInfo)
                    }
                }
        }
    }

    companion object {
        const val TAG: String = "EpochInfoUpdater"
    }
}
