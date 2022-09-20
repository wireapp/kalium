package com.wire.kalium.persistence.kmmSettings

import com.wire.kalium.persistence.client.LastRetrievedNotificationEventStorage

expect class UserPrefProvider {
    val lastRetrievedNotificationEventStorage: LastRetrievedNotificationEventStorage
    fun clear()
}
