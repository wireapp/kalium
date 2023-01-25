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
