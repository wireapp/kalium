package com.wire.kalium.persistence.kmmSettings

import com.wire.kalium.persistence.client.LastRetrievedNotificationEventStorage
import com.wire.kalium.persistence.event.EventInfoStorage

actual class UserPrefProvider {

    // TODO: Implement the preferences for iOS.
    private val kaliumPreferences = KaliumPreferencesSettings(EncryptedSettingsHolder("service").encryptedSettings)
    actual val lastRetrievedNotificationEventStorage: LastRetrievedNotificationEventStorage
        get() = TODO("Not yet implemented")

    actual fun clear() {
    }

    actual val eventInfoStorage: EventInfoStorage
        get() = TODO("Not yet implemented")

}
