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

package com.wire.kalium.logic.feature.message

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.time.Duration

@Suppress("LongParameterList")
/**
 * Sending a ping/knock message to a conversation
 */
class SendKnockUseCase internal constructor(
    private val persistMessage: PersistMessageUseCase,
    private val selfUserId: QualifiedID,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val slowSyncRepository: SlowSyncRepository,
    private val messageSender: MessageSender,
    private val messageSendFailureHandler: MessageSendFailureHandler,
    private val selfDeleteTimer: ObserveSelfDeletionTimerSettingsForConversationUseCase,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) {

    /**
     * Operation to send a ping or knock message to a conversation
     *
     * @param conversationId the id of the conversation to send the ping to
     * @param hotKnock whether to send this as a hot knock or not @see [MessageContent.Knock]
     * @return [Either] [CoreFailure] or [Unit] //fixme: we should not return [Either]
     */
    suspend operator fun invoke(conversationId: ConversationId, hotKnock: Boolean): Either<CoreFailure, Unit> = withContext(dispatcher.io) {
        slowSyncRepository.slowSyncStatus.first {
            it is SlowSyncStatus.Complete
        }

        val generatedMessageUuid = uuid4().toString()
        val messageTimer: Duration? = selfDeleteTimer(conversationId, true)
            .first()
            .duration

        return@withContext currentClientIdProvider().flatMap { currentClientId ->
            val message = Message.Regular(
                id = generatedMessageUuid,
                content = MessageContent.Knock(hotKnock),
                conversationId = conversationId,
                date = Clock.System.now(),
                senderUserId = selfUserId,
                senderClientId = currentClientId,
                status = Message.Status.Pending,
                editStatus = Message.EditStatus.NotEdited,
                isSelfMessage = true,
                expirationData = messageTimer?.let { Message.ExpirationData(it) }
            )
            persistMessage(message)
                .flatMap { messageSender.sendMessage(message) }
        }.onFailure { messageSendFailureHandler.handleFailureAndUpdateMessageStatus(it, conversationId, generatedMessageUuid, TYPE) }
    }

    companion object {
        const val TYPE = "Knock"
    }
}
