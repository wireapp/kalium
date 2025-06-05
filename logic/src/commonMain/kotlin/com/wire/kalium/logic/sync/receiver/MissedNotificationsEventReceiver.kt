/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository

/**
 * Internal event receiver for missed notifications, will trigger a full sync.
 */
internal interface MissedNotificationsEventReceiver : EventReceiver<Event.AsyncMissed>

internal class MissedNotificationsEventReceiverImpl(
    private val slowSyncRequester: suspend () -> Either<CoreFailure, Unit>,
    private val slowSyncRepository: SlowSyncRepository,
    private val eventRepository: EventRepository
) : MissedNotificationsEventReceiver {

    override suspend fun onEvent(event: Event.AsyncMissed, deliveryInfo: EventDeliveryInfo): Either<CoreFailure, Unit> {
        slowSyncRepository.clearLastSlowSyncCompletionInstant()
        return slowSyncRequester.invoke()
            .flatMap {
                eventRepository.acknowledgeMissedEvent()
            }
    }
}
