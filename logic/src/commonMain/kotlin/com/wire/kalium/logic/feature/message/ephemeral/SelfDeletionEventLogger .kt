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
package com.wire.kalium.logic.feature.message.ephemeral

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import com.wire.kalium.util.serialization.toJsonElement
import kotlinx.datetime.Instant
import kotlin.time.Duration

object SelfDeletionEventLogger {
    fun log(
        loggingSelfDeletionEvent: LoggingSelfDeletionEvent
    ) {
        kaliumLogger.i(loggingSelfDeletionEvent.toJson())
    }
}

sealed class LoggingSelfDeletionEvent(
    open val message: Message.Regular,
    open val expirationData: Message.ExpirationData
) {
    private companion object {
        const val EPHEMERAL_LOG_TAG = "Self-Deletion"
    }

    fun toJson(): String {
        return EPHEMERAL_LOG_TAG + mapOf(
            "message-id" to message.id,
            "conversation-id" to message.conversationId.toLogString(),
            "expire-after" to expirationData.expireAfter.inWholeSeconds.toString(),
            "expire-start-time" to expireStartTimeElement().toString()
        ).toMutableMap().plus(eventJsonMap()).toJsonElement().toString()
    }

    abstract fun eventJsonMap(): Map<String, String>

    private fun expireStartTimeElement(): String? {
        return when (val selfDeletionStatus = expirationData.selfDeletionStatus) {
            Message.ExpirationData.SelfDeletionStatus.NotStarted -> null
            is Message.ExpirationData.SelfDeletionStatus.Started ->
                selfDeletionStatus.selfDeletionStartDate.toIsoDateTimeString()
        }
    }

    data class SelfSelfDeletionAlreadyRequested(
        override val message: Message.Regular,
        override val expirationData: Message.ExpirationData
    ) : LoggingSelfDeletionEvent(message, expirationData) {
        override fun eventJsonMap(): Map<String, String> {
            return mapOf(
                "deletion-status" to "self-deletion-already-requested"
            )
        }
    }

    data class MarkingSelfSelfDeletionStartDate(
        override val message: Message.Regular,
        override val expirationData: Message.ExpirationData,
        val startDate: Instant
    ) : LoggingSelfDeletionEvent(message, expirationData) {
        override fun eventJsonMap(): Map<String, String> {
            return mapOf(
                "deletion-status" to "marking-self_deletion_start_date",
                "start-date-mark" to startDate.toIsoDateTimeString()
            )
        }
    }

    data class WaitingForSelfDeletion(
        override val message: Message.Regular,
        override val expirationData: Message.ExpirationData,
        val delayWaitTime: Duration
    ) : LoggingSelfDeletionEvent(message, expirationData) {
        override fun eventJsonMap(): Map<String, String> {
            return mapOf(
                "deletion-status" to "waiting-for-deletion",
                "delay-wait-time" to delayWaitTime.inWholeSeconds.toString()
            )
        }
    }

    data class StartingSelfSelfDeletion(
        override val message: Message.Regular,
        override val expirationData: Message.ExpirationData,
    ) : LoggingSelfDeletionEvent(message, expirationData) {
        override fun eventJsonMap(): Map<String, String> {
            return mapOf(
                "deletion-status" to "starting-self-deletion"
            )
        }
    }

    data class AttemptingToDelete(
        override val message: Message.Regular,
        override val expirationData: Message.ExpirationData
    ) : LoggingSelfDeletionEvent(message, expirationData) {
        override fun eventJsonMap(): Map<String, String> {
            return mapOf(
                "deletion-status" to "attempting-to-delete",
                "is-self-user-sender" to message.isSelfMessage.toString()
            )
        }
    }

    data class SuccessfullyDeleted(
        override val message: Message.Regular,
        override val expirationData: Message.ExpirationData,
    ) : LoggingSelfDeletionEvent(message, expirationData) {
        override fun eventJsonMap(): Map<String, String> {
            return mapOf(
                "deletion-status" to "self-deletion-succeed",
                "is-self-user-sender" to message.isSelfMessage.toString()
            )
        }
    }

    data class InvalidMessageStatus(
        override val message: Message.Regular,
        override val expirationData: Message.ExpirationData
    ) : LoggingSelfDeletionEvent(message, expirationData) {
        override fun eventJsonMap(): Map<String, String> {
            return mapOf(
                "deletion-status" to "invalid-message-status",
                "is-self-user-sender" to message.isSelfMessage.toString(),
            )
        }
    }
    data class SelfDeletionFailed(
        override val message: Message.Regular,
        override val expirationData: Message.ExpirationData,
        val coreFailure: CoreFailure
    ) : LoggingSelfDeletionEvent(message, expirationData) {
        override fun eventJsonMap(): Map<String, String> {
            return mapOf(
                "deletion-status" to "self-deletion-failed",
                "is-self-user-sender" to message.isSelfMessage.toString(),
                "reason" to coreFailure.toString()
            )
        }
    }
}
