package com.wire.kalium.persistence.kmmSettings

import com.wire.kalium.persistence.client.LastRetrievedNotificationEventStorage
import com.wire.kalium.persistence.client.LastRetrievedNotificationEventStorageImpl
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.event.EventInfoStorage
import com.wire.kalium.persistence.event.EventInfoStorageImpl

actual class UserPrefProvider(
    userId: UserIDEntity,
    rootPath: String,
    shouldEncryptData: Boolean = true
) {

    private val kaliumPref =
        KaliumPreferencesSettings(
            EncryptedSettingsHolder(rootPath, SettingOptions.UserSettings(shouldEncryptData, userId)).encryptedSettings
        )

    actual val lastRetrievedNotificationEventStorage: LastRetrievedNotificationEventStorage
        get() = LastRetrievedNotificationEventStorageImpl(kaliumPref)

    actual val eventInfoStorage: EventInfoStorage
        get() = EventInfoStorageImpl(kaliumPref)

    actual fun clear() {
        kaliumPref.nuke()
    }
}
