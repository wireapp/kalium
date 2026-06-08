/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.calling.notifications

internal class DefaultAvsCallNotificationProcessor(
    private val gateway: AvsCallNotificationNativeGateway
) : AvsCallNotificationProcessor {

    override fun process(notifications: List<AvsCallNotification>): AvsCallNotificationProcessingResult {
        val failure = notifications.validationFailure()
            ?: gateway.start().failureOrNull()
            ?: processStartedNotifications(notifications)

        return failure?.let { AvsCallNotificationProcessingResult.Failure(it) }
            ?: AvsCallNotificationProcessingResult.Success
    }

    private fun processStartedNotifications(notifications: List<AvsCallNotification>): AvsCallNotificationProcessingFailure? {
        var processFailure: AvsCallNotificationProcessingFailure? = null
        try {
            for ((index, notification) in notifications.withIndex()) {
                val failure = gateway.process(notification, index).failureOrNull()
                if (failure != null) {
                    processFailure = failure
                    break
                }
            }
        } finally {
            val endFailure = gateway.end().failureOrNull()
            if (processFailure == null && endFailure != null) {
                processFailure = endFailure
            }
        }

        return processFailure
    }

    override fun close() {
        gateway.close()
    }
}

private fun List<AvsCallNotification>.validationFailure(): AvsCallNotificationProcessingFailure? {
    val emptyPayloadIndex = indexOfFirst { it.payload.isEmpty() }
    return when {
        isEmpty() -> AvsCallNotificationProcessingFailure.EmptyEvents
        emptyPayloadIndex >= 0 -> AvsCallNotificationProcessingFailure.EmptyPayload(emptyPayloadIndex)
        else -> null
    }
}

internal interface AvsCallNotificationNativeGateway {
    fun start(): AvsCallNotificationNativeResult
    fun process(notification: AvsCallNotification, notificationIndex: Int): AvsCallNotificationNativeResult
    fun end(): AvsCallNotificationNativeResult
    fun close()
}

internal sealed interface AvsCallNotificationNativeResult {
    data object Success : AvsCallNotificationNativeResult

    data class Failure(
        val reason: AvsCallNotificationProcessingFailure.NativeFailure
    ) : AvsCallNotificationNativeResult
}

internal fun AvsCallNotificationNativeResult.failureOrNull(): AvsCallNotificationProcessingFailure.NativeFailure? =
    (this as? AvsCallNotificationNativeResult.Failure)?.reason
