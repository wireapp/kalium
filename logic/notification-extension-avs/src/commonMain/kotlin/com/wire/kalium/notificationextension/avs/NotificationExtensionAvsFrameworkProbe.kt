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

package com.wire.kalium.notificationextension.avs

/** Disposable non-production framework-load probe. */
public class NotificationExtensionAvsFrameworkProbe {
    public fun run(): String {
        val result = NotificationExtensionAvsProcessor().process(
            selfUserId = "synthetic-avs-user",
            selfClientId = "synthetic-avs-client",
            events = listOf(
                NotificationExtensionAvsEvent(
                    payload = "{}",
                    currentTimeSeconds = 1,
                    messageTimeSeconds = 1,
                    conversationId = "synthetic-avs-conversation",
                    senderUserId = "synthetic-avs-sender",
                    senderClientId = "synthetic-avs-sender-client",
                    conversationType = NotificationExtensionAvsConversationType.ONE_ON_ONE
                )
            ),
            callbacks = NoOpNotificationExtensionAvsCallbacks
        )
        return "splitAvs=true; facadeReturned=${result.status}; realPayload=false"
    }
}

private object NoOpNotificationExtensionAvsCallbacks : NotificationExtensionAvsCallbacks {
    override fun onIncomingCall(incomingCall: NotificationExtensionAvsIncomingCall): Unit = Unit
    override fun onMissedCall(missedCall: NotificationExtensionAvsMissedCall): Unit = Unit
    override fun onClosedCall(closedCall: NotificationExtensionAvsClosedCall): Unit = Unit
}
