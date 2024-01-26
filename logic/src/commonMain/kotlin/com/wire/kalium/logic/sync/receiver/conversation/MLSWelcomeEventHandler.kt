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

package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventLoggingStatus
import com.wire.kalium.logic.data.event.logEventProcessing
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesResult
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapMLSRequest
import io.ktor.util.decodeBase64Bytes
import kotlinx.coroutines.flow.first

interface MLSWelcomeEventHandler {
    suspend fun handle(event: Event.Conversation.MLSWelcome): Either<CoreFailure, Unit>
}

internal class MLSWelcomeEventHandlerImpl(
    val mlsClientProvider: MLSClientProvider,
    val conversationRepository: ConversationRepository,
    val oneOnOneResolver: OneOnOneResolver,
    val refillKeyPackages: RefillKeyPackagesUseCase
) : MLSWelcomeEventHandler {
    override suspend fun handle(event: Event.Conversation.MLSWelcome): Either<CoreFailure, Unit> =
        mlsClientProvider
            .getMLSClient()
            .flatMap { client ->
                wrapMLSRequest {
                    client.processWelcomeMessage(event.message.decodeBase64Bytes())
                }
            }.flatMap { groupID ->
                conversationRepository.fetchConversationIfUnknown(event.conversationId).map { groupID }
            }.flatMap { welcomeBundle ->
                markConversationAsEstablished(GroupID(welcomeBundle.groupId))
                // TODO: process crlDps from welcomeBundle
            }.flatMap {
                resolveConversationIfOneOnOne(event.conversationId)
            }
            .onSuccess {
                val didSucceedRefillingKeyPackages = when (val refillResult = refillKeyPackages()) {
                    is RefillKeyPackagesResult.Failure -> {
                        val exception = (refillResult.failure as? CoreFailure.Unknown)?.rootCause
                        kaliumLogger.w("Failed to refill key packages; Failure: ${refillResult.failure}", exception)
                        false
                    }

                    RefillKeyPackagesResult.Success -> {
                        true
                    }
                }
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.SUCCESS,
                        event,
                        "info" to "Established mls conversation from welcome message",
                        "didSucceedRefillingKeypackages" to didSucceedRefillingKeyPackages
                    )
            }.onFailure {
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.FAILURE,
                        event,
                        Pair("failure", it)
                    )
            }

    private suspend fun markConversationAsEstablished(groupID: GroupID): Either<CoreFailure, Unit> =
        conversationRepository.updateConversationGroupState(groupID, Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED)

    private suspend fun resolveConversationIfOneOnOne(conversationId: ConversationId): Either<CoreFailure, Unit> =
        conversationRepository.observeConversationDetailsById(conversationId)
            .first()
            .flatMap {
                if (it is ConversationDetails.OneOne) {
                    oneOnOneResolver.resolveOneOnOneConversationWithUser(
                        user = it.otherUser,
                        invalidateCurrentKnownProtocols = true
                    ).map { Unit }
                } else {
                    Either.Right(Unit)
                }
            }

}
