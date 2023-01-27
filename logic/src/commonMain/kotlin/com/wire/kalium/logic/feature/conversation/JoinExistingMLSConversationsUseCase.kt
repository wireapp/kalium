/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo.MLS.GroupState
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.kaliumLogger

/**
 * Send an external commit to join all MLS conversations for which the user is a member,
 * but has not yet joined the corresponding MLS group.
 */
interface JoinExistingMLSConversationsUseCase {
    suspend operator fun invoke(keepRetryingOnFailure: Boolean = true): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList")
class JoinExistingMLSConversationsUseCaseImpl(
    private val featureSupport: FeatureSupport,
    private val clientRepository: ClientRepository,
    private val conversationRepository: ConversationRepository,
    private val joinExistingMLSConversationUseCase: JoinExistingMLSConversationUseCase
) : JoinExistingMLSConversationsUseCase {

    override suspend operator fun invoke(keepRetryingOnFailure: Boolean): Either<CoreFailure, Unit> =
        if (!featureSupport.isMLSSupported ||
            !clientRepository.hasRegisteredMLSClient().getOrElse(false)
        ) {
            kaliumLogger.d("Skip re-join existing MLS conversation(s), since MLS is not supported.")
            Either.Right(Unit)
        } else {
            conversationRepository.getConversationsByGroupState(GroupState.PENDING_JOIN).flatMap { pendingConversations ->
                kaliumLogger.d("Requesting to re-join ${pendingConversations.size} existing MLS conversation(s)")

                return pendingConversations.map { conversation ->
                    joinExistingMLSConversationUseCase(conversation.id)
                }.foldToEitherWhileRight(Unit) { value, _ ->
                    value
                }
            }
        }
}
