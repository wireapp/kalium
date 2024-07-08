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
            buildSettings(
                SettingOptions.UserSettings(shouldEncryptData, userId),
                EncryptedSettingsPlatformParam(rootPath)
            )
        )

    actual val lastRetrievedNotificationEventStorage: LastRetrievedNotificationEventStorage
        get() = LastRetrievedNotificationEventStorageImpl(kaliumPref)
    actual val userConfigStorage: UserConfigStorage = UserConfigStorageImpl(kaliumPref)

    actual fun clear() {
        kaliumPref.nuke()
    }

}
