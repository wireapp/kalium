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

    private val kaliumPreferences =
        KaliumPreferencesSettings(
            encryptedSettingsBuilder(SettingOptions.UserSettings(shouldEncryptData, userId), EncryptedSettingsPlatformParam(rootPath))
        )

    actual val lastRetrievedNotificationEventStorage: LastRetrievedNotificationEventStorage
        get() = LastRetrievedNotificationEventStorageImpl(kaliumPreferences)

    actual fun clear() {
        kaliumPreferences.nuke()
    }

    actual val userConfigStorage: UserConfigStorage =
        UserConfigStorageImpl(kaliumPreferences)

}
