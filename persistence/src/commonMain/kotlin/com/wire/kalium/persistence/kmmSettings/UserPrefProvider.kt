package com.wire.kalium.persistence.kmmSettings

import com.wire.kalium.persistence.client.LastRetrievedNotificationEventStorage
import com.wire.kalium.persistence.config.UserConfigStorage

expect class UserPrefProvider {
    val lastRetrievedNotificationEventStorage: LastRetrievedNotificationEventStorage
    val userConfigStorage: UserConfigStorage
    fun clear()
}
