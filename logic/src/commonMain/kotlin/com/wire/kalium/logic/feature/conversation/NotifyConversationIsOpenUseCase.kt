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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessagesAfterEndDateUseCase
import com.wire.kalium.logic.functional.Either
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Used by the UI to notify Kalium that a conversation is open.
 * It's useful so Kalium can lazily perform update operations and make sure
 * that the conversation is up-to-date when the user opens it.
 *
 * One such operation is protocol reevaluation. So we can make sure
 * that the conversation is using the latest agreed protocol when the user
 * opens it.
 */
interface NotifyConversationIsOpenUseCase {
    suspend operator fun invoke(conversationId: ConversationId)
}

internal class NotifyConversationIsOpenUseCaseImpl(
    private val oneOnOneResolver: OneOnOneResolver,
    private val conversationRepository: ConversationRepository,
    private val deleteEphemeralMessageEndDate: DeleteEphemeralMessagesAfterEndDateUseCase,
    private val kaliumLogger: KaliumLogger
) : NotifyConversationIsOpenUseCase {

    override suspend operator fun invoke(conversationId: ConversationId) {
        kaliumLogger.v(
            "Notifying that conversation with ID: ${conversationId.toLogString()} is open"
        )
        val conversation = conversationRepository.observeConversationDetailsById(conversationId)
            .filterIsInstance<Either.Right<ConversationDetails>>()
            .map { it.value }
            .first()

        if (conversation is ConversationDetails.OneOne) {
            kaliumLogger.v(
                "Reevaluating protocol for 1:1 conversation with ID: ${conversationId.toLogString()}"
            )
            oneOnOneResolver.resolveOneOnOneConversationWithUser(
                user = conversation.otherUser,
                invalidateCurrentKnownProtocols = true
            )
        }

        // Delete Ephemeral Messages that has passed the end date
        deleteEphemeralMessageEndDate()
    }
}
