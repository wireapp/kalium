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

package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.ClientsRequestHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.callingLogger
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.EpochInfo
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdater
import io.mockative.Mockable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

internal class OnClientsRequest(
    private val conversationClientsInCallUpdater: ConversationClientsInCallUpdater,
    private val epochInfoUpdater: EpochInfoUpdater,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val callingScope: CoroutineScope,
    private val callRepository: CallRepository,
) : ClientsRequestHandler {

    override fun onClientsRequest(inst: Handle, conversationId: String, arg: Pointer?) {
        callingScope.launch {
            callingLogger.d("[OnClientsRequest] -> ConversationId: ${conversationId.obfuscateId()}")
            val conversationIdWithDomain = qualifiedIdMapper.fromStringToQualifiedID(conversationId)
            conversationClientsInCallUpdater(conversationIdWithDomain)

            // Update AVS with current EpochInfo for MLS calls when clients are requested
            if (callRepository.getCallMetadata(conversationIdWithDomain)?.protocol is Conversation.ProtocolInfo.MLS) {
                callRepository.observeEpochInfo(conversationIdWithDomain)
                    .flatMap { it.firstOrNull()?.right() ?: StorageFailure.DataNotFound.left() }
                    .onFailure {
                        callingLogger.d(
                            "[OnClientsRequest] -> Failure when trying to get current epoch info " +
                                    "| ConversationId: ${conversationId.obfuscateId()} | Error: $it"
                        )
                    }
                    .onSuccess { epochInfo: EpochInfo ->
                        callingLogger.d(
                            "[OnClientsRequest] -> Updating epoch info " +
                                    "| ConversationId: ${conversationId.obfuscateId()} | Epoch info: $epochInfo"
                        )
                        epochInfoUpdater.updateEpochInfo(conversationIdWithDomain, epochInfo)
                    }
            }
        }
    }
}

@Mockable
internal fun interface EpochInfoUpdater {
    suspend fun updateEpochInfo(conversationId: ConversationId, epochInfo: EpochInfo)
}
