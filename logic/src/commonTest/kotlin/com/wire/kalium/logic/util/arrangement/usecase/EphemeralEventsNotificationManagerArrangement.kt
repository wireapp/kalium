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
package com.wire.kalium.logic.util.arrangement.usecase

import com.wire.kalium.logic.data.notification.LocalNotification
import com.wire.kalium.logic.feature.message.EphemeralEventsNotificationManager
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.thenDoNothing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal interface EphemeralEventsNotificationManagerArrangement {

    val ephemeralNotifications: EphemeralEventsNotificationManager
    fun withEphemeralNotification(ephemeralsFlow: Flow<LocalNotification> = flowOf())
}

internal open class EphemeralEventsNotificationManagerArrangementImpl : EphemeralEventsNotificationManagerArrangement {

    @Mock
    override val ephemeralNotifications = mock(classOf<EphemeralEventsNotificationManager>())

    init {
        given(ephemeralNotifications)
            .suspendFunction(ephemeralNotifications::scheduleDeleteConversationNotification)
            .whenInvokedWith(any())
            .thenDoNothing()
        given(ephemeralNotifications)
            .suspendFunction(ephemeralNotifications::scheduleDeleteMessageNotification)
            .whenInvokedWith(any())
            .thenDoNothing()
        given(ephemeralNotifications)
            .suspendFunction(ephemeralNotifications::scheduleEditMessageNotification)
            .whenInvokedWith(any())
            .thenDoNothing()
    }

    override fun withEphemeralNotification(ephemeralsFlow: Flow<LocalNotification>) {
        given(ephemeralNotifications)
            .suspendFunction(ephemeralNotifications::observeEphemeralNotifications)
            .whenInvoked()
            .thenReturn(ephemeralsFlow)
    }
}
