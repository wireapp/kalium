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
package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onlyRight
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan

/**
 * End call when conversation is deleted, user is not a member anymore or user is deleted.
 */
interface EndCallOnConversationChangeUseCase {
    suspend operator fun invoke()
}

internal class EndCallOnConversationChangeUseCaseImpl(
    private val callRepository: CallRepository,
    private val conversationRepository: ConversationRepository,
    private val endCallUseCase: EndCallUseCase,
    private val endCallListener: EndCallResultListener
) : EndCallOnConversationChangeUseCase {
    override suspend operator fun invoke() {
        val callsFlow = callRepository.establishedCallsFlow().map { calls ->
            calls.map { it.conversationId }
        }.distinctUntilChanged().cancellable()

        callsFlow.flatMapLatest { calls ->
            if (calls.isEmpty()) return@flatMapLatest emptyFlow()

            val currentCall = calls.first()

            merge(
                finishCallBecauseOfMembershipChangesFlow(currentCall),
                finishCallBecauseOfVerificationDegradedFlow(currentCall)
            )
        }.collect { conversationId -> endCallUseCase(conversationId) }
    }

    private suspend fun finishCallBecauseOfMembershipChangesFlow(conversationId: ConversationId) =
        conversationRepository.observeConversationDetailsById(conversationId).cancellable()
            .map { conversationDetails ->
                conversationDetails.map {
                    // Member blocked or deleted
                    val isOtherUserBlockedOrDeleted = it is ConversationDetails.OneOne
                            && (it.otherUser.deleted || it.otherUser.connectionStatus == ConnectionState.BLOCKED)
                    // Not a member of group anymore
                    val isSelfRemovedFromGroup = it is ConversationDetails.Group && !it.isSelfUserMember

                    isOtherUserBlockedOrDeleted || isSelfRemovedFromGroup
                }.getOrElse(true)
            }
            .filter { it }
            .map { conversationId }

    /**
     * @return [ConversationId] only when the conversation Proteus or MLS verification status was verified in past
     * but became not verified -> means need to finish the call
     */
    private suspend fun finishCallBecauseOfVerificationDegradedFlow(conversationId: ConversationId) =
        conversationRepository.observeConversationDetailsById(conversationId)
            .cancellable()
            .onlyRight()
            .map {
                val isProteusVerified = it.conversation.proteusVerificationStatus == Conversation.VerificationStatus.VERIFIED
                val isMLSVerified = it.conversation.mlsVerificationStatus == Conversation.VerificationStatus.VERIFIED

                isProteusVerified to isMLSVerified
            }
            .scan(ConversationVerificationStatuses()) { prevState, (isProteusVerified, isMLSVerified) ->
                ConversationVerificationStatuses(
                    isProteusVerified = isProteusVerified,
                    wasProteusVerified = prevState.isProteusVerified,
                    isMLSVerified = isMLSVerified,
                    wasMLSVerified = prevState.isMLSVerified
                )
            }
            .filter { it.shouldFinishCall() }
            .map {
                endCallListener.onCallEndedBecauseOfVerificationDegraded()
                conversationId
            }

    private data class ConversationVerificationStatuses(
        val isProteusVerified: Boolean = false,
        val wasProteusVerified: Boolean = false,
        val isMLSVerified: Boolean = false,
        val wasMLSVerified: Boolean = false
    ) {
        fun shouldFinishCall(): Boolean = (!isProteusVerified && wasProteusVerified) || (!isMLSVerified && wasMLSVerified)
    }
}
