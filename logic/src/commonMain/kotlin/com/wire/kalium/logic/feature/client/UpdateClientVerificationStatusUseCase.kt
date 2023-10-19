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
package com.wire.kalium.logic.feature.client

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.util.DateTimeUtil

/**
 * Updates the verification status of a client.
 * @param userId The user id of the client.
 * @param clientId The client id of the client.
 * @param verified The new verification status of the client.
 * @return [UpdateClientVerificationStatusUseCase.Result.Success] if the client was updated successfully.
 * [UpdateClientVerificationStatusUseCase.Result.Failure] if the client could not be updated.
 */
class UpdateClientVerificationStatusUseCase internal constructor(
    private val clientRepository: ClientRepository,
    private val conversationRepository: ConversationRepository,
    private val persistMessage: PersistMessageUseCase,
    private val selfUserId: UserId,
) {
    suspend operator fun invoke(userId: UserId, clientId: ClientId, verified: Boolean): Result =
        clientRepository.updateClientProteusVerificationStatus(userId, clientId, verified)
            .flatMap { conversationRepository.getConversationsProteusVerificationDataByClientId(clientId) }
            .flatMap { updateConversationsStatusIfNeeded(it) }
            .map { it.forEach { (conversationId, newStatus) -> notifyUserAboutStatusChanges(conversationId, newStatus) } }
            .fold(
                { error -> Result.Failure(error) },
                { Result.Success }
            )

    sealed interface Result {
        data object Success : Result
        data class Failure(val error: StorageFailure) : Result
    }

    /**
     * Select the conversations that [ClientId] belongs to AND which should update the Proteus verification status
     * according to changed conditions:
     * - if the client was the last not verified in the conversation and user verified it -> conversation becomes VERIFIED;
     * - if the conversation was verified and user un-verify at least 1 client -> conversation becomes DEGRADED;
     * And updates the conversations statuses.
     */
    private suspend fun updateConversationsStatusIfNeeded(
        statusesData: List<Conversation.ProteusVerificationData>
    ): Either<StorageFailure, MutableMap<QualifiedID, Conversation.VerificationStatus>> {
        val mapForUpdatingStatuses = mutableMapOf<QualifiedID, Conversation.VerificationStatus>()

        statusesData.forEach {
            if (it.isActuallyVerified && it.currentVerificationStatus != Conversation.VerificationStatus.VERIFIED) {
                mapForUpdatingStatuses[it.conversationId] = Conversation.VerificationStatus.VERIFIED
            } else if (!it.isActuallyVerified && it.currentVerificationStatus == Conversation.VerificationStatus.VERIFIED) {
                mapForUpdatingStatuses[it.conversationId] = Conversation.VerificationStatus.DEGRADED
            }
        }

        return if (mapForUpdatingStatuses.isEmpty()) Either.Right(mapForUpdatingStatuses)
        else conversationRepository.updateProteusVerificationStatuses(mapForUpdatingStatuses)
            .map { mapForUpdatingStatuses }
    }

    /**
     * Add a SystemMessage into a conversation, to inform user when the conversation verification status was changed.
     */
    private suspend fun notifyUserAboutStatusChanges(
        conversationId: ConversationId,
        updatedStatus: Conversation.VerificationStatus
    ) {
        val content = if (updatedStatus == Conversation.VerificationStatus.VERIFIED) MessageContent.ConversationVerifiedProteus
        else MessageContent.ConversationDegradedProteus

        val conversationDegradedMessage = Message.System(
            id = uuid4().toString(),
            content = content,
            conversationId = conversationId,
            date = DateTimeUtil.currentIsoDateTimeString(),
            senderUserId = selfUserId,
            status = Message.Status.Sent,
            visibility = Message.Visibility.VISIBLE,
            expirationData = null
        )

        persistMessage(conversationDegradedMessage)
    }
}
