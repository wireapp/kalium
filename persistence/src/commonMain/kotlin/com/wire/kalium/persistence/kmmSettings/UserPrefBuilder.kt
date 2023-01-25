package com.wire.kalium.persistence.kmmSettings

import com.wire.kalium.persistence.client.LastRetrievedNotificationEventStorage

expect class UserPrefBuilder {
    val lastRetrievedNotificationEventStorage: LastRetrievedNotificationEventStorage
    fun clear()
}
