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

package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.event.Event

interface UserPropertiesEventReceiver : EventReceiver<Event.UserProperty>

class UserPropertiesEventReceiverImpl internal constructor(
    private val userConfigRepository: UserConfigRepository
) : UserPropertiesEventReceiver {

    override suspend fun onEvent(event: Event.UserProperty) {
        when (event) {
            is Event.UserProperty.ReadReceiptModeSet -> handleReadReceiptMode(event)
        }
    }

    private suspend fun handleReadReceiptMode(event: Event.UserProperty.ReadReceiptModeSet) {
        userConfigRepository.setReadReceiptsStatus(event.value)
    }

    private companion object {
        const val TAG = "UserPropertiesEventReceiver"
    }
}
