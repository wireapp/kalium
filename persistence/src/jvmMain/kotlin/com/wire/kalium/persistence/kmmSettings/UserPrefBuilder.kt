package com.wire.kalium.persistence.kmmSettings

import com.wire.kalium.persistence.client.LastRetrievedNotificationEventStorage
import com.wire.kalium.persistence.client.LastRetrievedNotificationEventStorageImpl
import com.wire.kalium.persistence.config.UserConfigStorage
import com.wire.kalium.persistence.config.UserConfigStorageImpl
import com.wire.kalium.persistence.dao.UserIDEntity

actual class UserPrefBuilder(
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
    actual val userConfigStorage: UserConfigStorage = UserConfigStorageImpl(kaliumPref)

    actual fun clear() {
        kaliumPref.nuke()
    }

}
