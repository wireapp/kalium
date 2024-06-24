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

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.EventLoggingStatus
import com.wire.kalium.logic.util.createEventProcessingLogger
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.serialization.toJsonElement

interface MemberChangeEventHandler {
    suspend fun handle(event: Event.Conversation.MemberChanged)
}

internal class MemberChangeEventHandlerImpl(
    private val conversationRepository: ConversationRepository,
) : MemberChangeEventHandler {
    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun handle(event: Event.Conversation.MemberChanged) {
        val eventLogger = kaliumLogger.createEventProcessingLogger(event)
        when (event) {
            is Event.Conversation.MemberChanged.MemberMutedStatusChanged -> {
                conversationRepository.updateMutedStatusLocally(
                    event.conversationId,
                    event.mutedConversationStatus,
                    DateTimeUtil.currentInstant().toEpochMilliseconds()
                )
                eventLogger.logSuccess()
            }

            is Event.Conversation.MemberChanged.MemberArchivedStatusChanged -> {
                conversationRepository.updateArchivedStatusLocally(
                    event.conversationId,
                    event.isArchiving,
                    DateTimeUtil.currentInstant().toEpochMilliseconds()
                )
                eventLogger.logSuccess()
            }

            is Event.Conversation.MemberChanged.MemberChangedRole -> {
                handleMemberChangedRoleEvent(event)
            }

            else -> {
                eventLogger.logComplete(
                    EventLoggingStatus.SKIPPED,
                    arrayOf("info" to "Ignoring 'conversation.member-update' event, not handled yet")
                )
            }
        }
    }

    private suspend fun handleMemberChangedRoleEvent(event: Event.Conversation.MemberChanged.MemberChangedRole) {
        val eventLogger = kaliumLogger.createEventProcessingLogger(event)
        // Attempt to fetch conversation details if needed, as this might be an unknown conversation
        conversationRepository.fetchConversationIfUnknown(event.conversationId)
            .run {
                onSuccess {
                    val logMap = mapOf("event" to event.toLogMap())
                    logger.v("Succeeded fetching conversation details on MemberChange Event: ${logMap.toJsonElement()}")
                }
                onFailure {
                    val logMap = mapOf(
                        "event" to event.toLogMap(),
                        "errorInfo" to "$it"
                    )
                    logger.w("Failure fetching conversation details on MemberChange Event: ${logMap.toJsonElement()}")
                }
                // Even if unable to fetch conversation details, at least attempt updating the member
                conversationRepository.updateMemberFromEvent(event.member!!, event.conversationId)
            }
            .onFailure { eventLogger.logFailure(it) }
            .onSuccess { eventLogger.logSuccess() }
    }
}
