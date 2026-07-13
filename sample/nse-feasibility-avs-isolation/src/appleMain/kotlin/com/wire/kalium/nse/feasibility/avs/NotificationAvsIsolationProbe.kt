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

package com.wire.kalium.nse.feasibility.avs

import com.wire.kalium.calling.notifications.AvsCallNotificationCallbacks
import com.wire.kalium.calling.notifications.AvsCallNotification
import com.wire.kalium.calling.notifications.AvsCallNotificationConversationType
import com.wire.kalium.calling.notifications.AvsCallNotificationProcessorFactory
import com.wire.kalium.calling.notifications.AvsCallNotificationProcessingResult
import com.wire.kalium.calling.notifications.AvsClosedCallNotification
import com.wire.kalium.calling.notifications.AvsIncomingCallNotification
import com.wire.kalium.calling.notifications.AvsMissedCallNotification

/**
 * Disposable dynamic-framework probe for the native AVS/CoreCrypto symbol-isolation experiment.
 *
 * This framework deliberately owns AVS and no CoreCrypto dependency. The sibling feasibility
 * framework deliberately owns CoreCrypto and no AVS dependency. A Swift host loads and invokes
 * both frameworks in one process to validate Mach-O two-level namespace behavior.
 */
public class NotificationAvsIsolationProbe {

    public fun run(): String {
        val processor = AvsCallNotificationProcessorFactory.create(
            selfUserId = "feasibility-user",
            selfClientId = "feasibility-client",
            callbacks = NoOpAvsCallbacks
        )
        return try {
            val invalidButNonEmptyEvent = AvsCallNotification(
                payload = byteArrayOf('{'.code.toByte(), '}'.code.toByte()),
                currentTimeSeconds = 1u,
                messageTimeSeconds = 1u,
                conversationId = "feasibility-conversation",
                senderUserId = "feasibility-sender",
                senderClientId = "feasibility-sender-client",
                conversationType = AvsCallNotificationConversationType.OneOnOne
            )
            when (val result = processor.process(listOf(invalidButNonEmptyEvent))) {
                is AvsCallNotificationProcessingResult.Failure -> {
                    "factory created; non-empty native lifecycle returned ${result.reason}; close balanced"
                }

                AvsCallNotificationProcessingResult.Success ->
                    "factory created; non-empty native lifecycle succeeded; close balanced"
            }
        } finally {
            processor.close()
        }
    }
}

private object NoOpAvsCallbacks : AvsCallNotificationCallbacks {
    override fun onIncomingCall(incomingCall: AvsIncomingCallNotification) = Unit
    override fun onMissedCall(missedCall: AvsMissedCallNotification) = Unit
    override fun onClosedCall(closedCall: AvsClosedCallNotification) = Unit
}
