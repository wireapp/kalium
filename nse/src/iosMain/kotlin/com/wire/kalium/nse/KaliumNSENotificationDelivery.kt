/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.nse

import com.wire.kalium.logic.data.meeting.MeetingReminder
import com.wire.kalium.logic.data.notification.LocalNotification
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Opt-in native delivery. Callbacks must use only these immutable snapshots and native notification APIs. */
public interface KaliumNSENotificationDelivery : KaliumNSEMessageDelivery {
    /** Return true only after enqueue, or when the same account/meeting/event identity is already delivered. */
    public suspend fun deliverMeeting(alert: LocalNotification.Meeting): Boolean

    /** Replace this account's reminder snapshot, preserving the global iOS capacity and other accounts. */
    public suspend fun synchronizeMeetingReminders(reminders: List<MeetingReminder>)
}

internal suspend fun deliverMeetingAlerts(
    alerts: List<LocalNotification.Meeting>,
    delivery: KaliumNSENotificationDelivery,
    acknowledge: suspend (LocalNotification.Meeting) -> Unit,
) {
    for (alert in alerts) {
        currentCoroutineContext().ensureActive()
        if (awaitNativeAcceptance { delivery.deliverMeeting(alert) }) {
            withContext(NonCancellable) { acknowledge(alert) }
        }
    }
}
