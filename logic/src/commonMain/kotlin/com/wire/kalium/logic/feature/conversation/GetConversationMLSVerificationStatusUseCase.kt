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
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.MLSVerificationStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Operation that fetches [MLSVerificationStatus] of the specific conversation
 *
 * @param conversationId
 * @return [MLSVerificationStatus] of the conversation
 */
interface GetConversationMLSVerificationStatusUseCase {
    suspend operator fun invoke(conversationId: ConversationId): MLSVerificationStatus
}

class GetConversationMLSVerificationStatusUseCaseImpl(
    private val featureSupport: FeatureSupport,
    private val clientRepository: ClientRepository,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : GetConversationMLSVerificationStatusUseCase {
    private val dispatcher = kaliumDispatcher.io

    override suspend fun invoke(conversationId: ConversationId): MLSVerificationStatus = withContext(dispatcher) {
        if (!featureSupport.isMLSSupported || !clientRepository.hasRegisteredMLSClient().getOrElse(false)) {
            kaliumLogger.d("Skip getting MLS verification status if conversation, since MLS is not supported.")
            Either.Right(MLSVerificationStatus.NOT_VERIFIED)
        } else {
            conversationRepository.baseInfoById(conversationId).fold(
                { Either.Left(StorageFailure.DataNotFound) },
                { getConversationMLSVerificationStatus(it) })
        }
            .getOrElse(MLSVerificationStatus.NOT_VERIFIED)
    }

    private suspend fun getConversationMLSVerificationStatus(conversation: Conversation): Either<CoreFailure, MLSVerificationStatus> =
        if (conversation.protocol is Conversation.ProtocolInfo.MLS) {
            mlsConversationRepository.getConversationVerificationStatus(conversation.protocol.groupId)
        } else {
            Either.Right(MLSVerificationStatus.NOT_VERIFIED)
        }
}
