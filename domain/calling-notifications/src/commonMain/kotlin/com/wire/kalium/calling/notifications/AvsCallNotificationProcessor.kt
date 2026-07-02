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

public interface AvsCallNotificationProcessor {
    public fun process(notifications: List<AvsCallNotification>): AvsCallNotificationProcessingResult
    public fun close()
}

public object AvsCallNotificationProcessorFactory {
    public fun create(
        selfUserId: String,
        selfClientId: String,
        callbacks: AvsCallNotificationCallbacks
    ): AvsCallNotificationProcessor = createPlatformAvsCallNotificationProcessor(
        selfUserId = selfUserId,
        selfClientId = selfClientId,
        callbacks = callbacks
    )
}

public sealed interface AvsCallNotificationProcessingResult {
    public data object Success : AvsCallNotificationProcessingResult

    public data class Failure(
        public val reason: AvsCallNotificationProcessingFailure
    ) : AvsCallNotificationProcessingResult
}

public sealed interface AvsCallNotificationProcessingFailure {
    public data object UnsupportedPlatform : AvsCallNotificationProcessingFailure
    public data object EmptyEvents : AvsCallNotificationProcessingFailure

    public data class EmptyPayload(
        public val eventIndex: Int
    ) : AvsCallNotificationProcessingFailure

    public data class NativeFailure(
        public val operation: AvsCallNotificationNativeOperation,
        public val code: Int? = null,
        public val eventIndex: Int? = null,
        public val message: String? = null
    ) : AvsCallNotificationProcessingFailure
}

public enum class AvsCallNotificationNativeOperation {
    Create,
    Start,
    Process,
    End
}

internal expect fun createPlatformAvsCallNotificationProcessor(
    selfUserId: String,
    selfClientId: String,
    callbacks: AvsCallNotificationCallbacks
): AvsCallNotificationProcessor
