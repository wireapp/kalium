package com.wire.kalium.logic.feature.notifications

import com.wire.kalium.logic.configuration.notification.LastNotificationEventDataSource
import com.wire.kalium.persistence.client.LastRetrievedNotificationEventStorage
import com.wire.kalium.persistence.client.LastRetrievedNotificationEventStorageImpl
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences

class LastNotificationEventScope(
    private val globalPreferences: KaliumPreferences
    ) {

    private val lastRetrievedNotificationStorage: LastRetrievedNotificationEventStorage
        get() = LastRetrievedNotificationEventStorageImpl(globalPreferences)
    private val lastNotificationEventRepository get() =
        LastNotificationEventDataSource(lastRetrievedNotificationStorage)

    val saveLastNotificationEvent get() = SaveLastNotificationEventUseCase(lastNotificationEventRepository)
    val getLastNotificationEvent get() = GetLastNotificationEventUseCase(lastNotificationEventRepository)
//    val synchLastNotificationEvent get() = SynchLastNotificationEventUseCase(lastNotificationEventRepository)
}
