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

package com.wire.kalium.logic.feature.debug

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

/**
 * This use case can be used by QA to send read and delivery receipts. This debug function can be used to test correct
 * client behaviour. It should not be used by clients itself.
 */
class SendConfirmationUseCase internal constructor(
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val slowSyncRepository: SlowSyncRepository,
    private val messageSender: MessageSender,
    private val selfUserId: UserId
) {

    suspend operator fun invoke(
        conversationId: ConversationId,
        type: ReceiptType,
        firstMessageId: String,
        moreMessageIds: List<String>
    ): Either<CoreFailure, Unit> {
        slowSyncRepository.slowSyncStatus.first {
            it is SlowSyncStatus.Complete
        }

        val generatedMessageUuid = uuid4().toString()

        return currentClientIdProvider().flatMap { currentClientId ->
            val message = Message.Signaling(
                id = generatedMessageUuid,
                content = MessageContent.Receipt(type, listOf(firstMessageId) + moreMessageIds),
                conversationId = conversationId,
                date = Clock.System.now(),
                senderUserId = selfUserId,
                senderClientId = currentClientId,
                status = Message.Status.Pending,
                isSelfMessage = true,
                expirationData = null
            )
            messageSender.sendMessage(message)
        }.onFailure {
            if (it is CoreFailure.Unknown) {
                kaliumLogger.e("There was an unknown error trying to send the message $it", it.rootCause)
                it.rootCause?.printStackTrace()
            } else {
                kaliumLogger.e("There was an error trying to send the message $it")
            }
        }
    }
}
