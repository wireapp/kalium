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
package com.wire.kalium.logic.feature.message.ephemeral

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import com.wire.kalium.util.serialization.toJsonElement
import kotlinx.datetime.Instant
import kotlin.time.Duration

internal class SelfDeletionEventLogger(private val logger: KaliumLogger) {
    fun log(
        loggingSelfDeletionEvent: LoggingSelfDeletionEvent
    ) {
        logger.i(loggingSelfDeletionEvent.toJson())
    }
}

internal sealed class LoggingSelfDeletionEvent(
    open val message: Message,
    open val expirationData: Message.ExpirationData
) {
    private companion object {
        const val EPHEMERAL_LOG_TAG = "Self-Deletion: "
    }

    fun toJson(): String {
        return EPHEMERAL_LOG_TAG + mapOf(
            "message" to (message as Message.Sendable).toLogMap(),
            "expiration-data" to expirationData.toLogMap(),
        )
            .toMutableMap()
            .plus(toLogMap())
            .toJsonElement()
            .toString()
    }

    abstract fun toLogMap(): Map<String, Any?>

    data class SelfSelfDeletionAlreadyRequested(
        override val message: Message,
        override val expirationData: Message.ExpirationData
    ) : LoggingSelfDeletionEvent(message, expirationData) {
        override fun toLogMap(): Map<String, Any?> {
            return mapOf(
                "deletion-info" to mapOf(
                    "info" to "self-deletion-already-requested",
                )
            )
        }
    }

    data class MarkingSelfSelfDeletionEndDate(
        override val message: Message,
        override val expirationData: Message.ExpirationData,
        val endDate: Instant
    ) : LoggingSelfDeletionEvent(message, expirationData) {
        override fun toLogMap(): Map<String, Any?> {
            return mapOf(
                "deletion-info" to mapOf(
                    "info" to "marking-self_deletion_end_date",
                    "end-date-mark" to endDate.toIsoDateTimeString()
                )
            )
        }
    }

    data class WaitingForSelfDeletion(
        override val message: Message,
        override val expirationData: Message.ExpirationData,
        val delayWaitTime: Duration
    ) : LoggingSelfDeletionEvent(message, expirationData) {
        override fun toLogMap(): Map<String, Any?> {
            return mapOf(
                "deletion-info" to mapOf(
                    "info" to "waiting-for-deletion",
                    "delay-wait-time" to delayWaitTime.inWholeSeconds.toString()
                )
            )
        }
    }

    data class StartingSelfSelfDeletion(
        override val message: Message,
        override val expirationData: Message.ExpirationData,
    ) : LoggingSelfDeletionEvent(message, expirationData) {
        override fun toLogMap(): Map<String, Any?> {
            return mapOf(
                "deletion-info" to mapOf(
                    "info" to "starting-self-deletion",
                )
            )
        }
    }

    data class AttemptingToDelete(
        override val message: Message,
        override val expirationData: Message.ExpirationData
    ) : LoggingSelfDeletionEvent(message, expirationData) {
        override fun toLogMap(): Map<String, Any?> {
            return mapOf(
                "deletion-status" to "attempting-to-delete"
            )
        }
    }

    data class SuccessfullyDeleted(
        override val message: Message,
        override val expirationData: Message.ExpirationData,
    ) : LoggingSelfDeletionEvent(message, expirationData) {
        override fun toLogMap(): Map<String, Any?> {
            return mapOf(
                "deletion-status" to "self-deletion-succeed",
            )
        }
    }

    data class InvalidMessageStatus(
        override val message: Message,
        override val expirationData: Message.ExpirationData
    ) : LoggingSelfDeletionEvent(message, expirationData) {
        override fun toLogMap(): Map<String, Any?> {
            return mapOf(
                "deletion-status" to "invalid-message-status"
            )
        }
    }

    data class SelfDeletionFailed(
        override val message: Message,
        override val expirationData: Message.ExpirationData,
        val coreFailure: CoreFailure
    ) : LoggingSelfDeletionEvent(message, expirationData) {
        override fun toLogMap(): Map<String, Any?> {
            return mapOf(
                "deletion-status" to "self-deletion-failed",
                "reason" to coreFailure.toString()
            )
        }
    }
}
