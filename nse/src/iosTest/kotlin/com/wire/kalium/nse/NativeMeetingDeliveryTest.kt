/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.nse

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.meeting.MeetingReminder
import com.wire.kalium.logic.data.notification.LocalNotification
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NativeMeetingDeliveryTest {
    private val alert = LocalNotification.Meeting.Cancel(
        "event", QualifiedID("meeting", "example.com"), QualifiedID("conversation", "example.com"),
        "Planning", null, Instant.parse("2026-11-04T12:00:00Z"),
    )

    @Test
    fun cancelledNativeMeetingEnqueueReleasesAccountAndCannotAcknowledgeLateAcceptance() = runTest {
        val callback = CompletableDeferred<Continuation<Boolean>>()
        val acknowledged = mutableListOf<String>()
        val operation = launch {
            deliverMeetingAlerts(listOf(alert), Delivery { suspendCoroutine { callback.complete(it) } }) {
                acknowledged.add(it.eventId)
            }
        }
        val completion = callback.await()
        operation.cancelAndJoin()
        completion.resume(true)
        runCurrent()
        assertEquals(emptyList(), acknowledged)
    }

    private class Delivery(private val enqueue: suspend (LocalNotification.Meeting) -> Boolean) : KaliumNSENotificationDelivery {
        override suspend fun deliver(alert: KaliumNSEAlert): Boolean = false
        override suspend fun deliverMeeting(alert: LocalNotification.Meeting): Boolean = enqueue(alert)
        override suspend fun synchronizeMeetingReminders(reminders: List<MeetingReminder>) = Unit
    }
}
