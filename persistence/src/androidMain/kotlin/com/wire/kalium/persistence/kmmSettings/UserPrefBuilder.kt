/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

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
            EncryptedSettingsBuilder.build(
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
