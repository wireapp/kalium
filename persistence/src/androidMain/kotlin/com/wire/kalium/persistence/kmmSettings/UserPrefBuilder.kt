package com.wire.kalium.persistence.kmmSettings

import android.content.Context
import com.wire.kalium.persistence.client.LastRetrievedNotificationEventStorage
import com.wire.kalium.persistence.client.LastRetrievedNotificationEventStorageImpl
import com.wire.kalium.persistence.config.UserConfigStorage
import com.wire.kalium.persistence.config.UserConfigStorageImpl
import com.wire.kalium.persistence.dao.UserIDEntity

actual class UserPrefBuilder(
    userId: UserIDEntity,
    context: Context,
    shouldEncryptData: Boolean = true
) {
    private val encryptedSettingsHolder =
        KaliumPreferencesSettings(
            encryptedSettingsBuilder(
                SettingOptions.UserSettings(shouldEncryptData = shouldEncryptData, userIDEntity = userId),
                EncryptedSettingsPlatformParam(context)
            )
        )

    actual val lastRetrievedNotificationEventStorage: LastRetrievedNotificationEventStorage
        get() = LastRetrievedNotificationEventStorageImpl(encryptedSettingsHolder)

    actual val userConfigStorage: UserConfigStorage = UserConfigStorageImpl(encryptedSettingsHolder)

    actual fun clear() {
        encryptedSettingsHolder.nuke()
    }
}
