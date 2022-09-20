package com.wire.kalium.persistence.kmmSettings

import android.content.Context
import com.wire.kalium.persistence.client.LastRetrievedNotificationEventStorage
import com.wire.kalium.persistence.client.LastRetrievedNotificationEventStorageImpl
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.event.EventInfoStorage
import com.wire.kalium.persistence.event.EventInfoStorageImpl

actual class UserPrefProvider(
    userId: UserIDEntity,
    context: Context,
    shouldEncryptData: Boolean = true
) {
    private val encryptedSettingsHolder =
        KaliumPreferencesSettings(
            EncryptedSettingsHolder(
                context,
                options = SettingOptions.UserSettings(shouldEncryptData = shouldEncryptData, userIDEntity = userId)
            ).encryptedSettings
        )

    actual val lastRetrievedNotificationEventStorage: LastRetrievedNotificationEventStorage
        get() = LastRetrievedNotificationEventStorageImpl(encryptedSettingsHolder)

    actual fun clear() {
        encryptedSettingsHolder.nuke()
    }

    actual val eventInfoStorage: EventInfoStorage
        get() = EventInfoStorageImpl(encryptedSettingsHolder)
}
