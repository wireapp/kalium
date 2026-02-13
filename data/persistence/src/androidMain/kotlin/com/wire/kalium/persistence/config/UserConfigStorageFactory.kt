/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.persistence.config

import android.content.Context
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.kmmSettings.EncryptedSettingsPlatformParam
import com.wire.kalium.persistence.kmmSettings.KaliumPreferencesSettings
import com.wire.kalium.persistence.kmmSettings.SettingOptions
import com.wire.kalium.persistence.kmmSettings.buildSettings

@Suppress("DEPRECATION")
@Deprecated(
    "Scheduled for removal in future versions, User KMM Settings are now replaced by database implementation." +
            "Just kept for migration purposes.",
    ReplaceWith("No replacement available"),
)
actual class UserConfigStorageFactory actual constructor() {
    /**
     * Creates a [UserConfigStorage] instance for Android.
     * @param userId The user ID entity
     * @param shouldEncryptData Whether to encrypt the data
     * @param platformParam Must be an Android [Context]
     */
    actual fun create(
        userId: UserIDEntity,
        shouldEncryptData: Boolean,
        platformParam: Any
    ): UserConfigStorage {
        require(platformParam is Context) { "platformParam must be an Android Context" }
        val settings = buildSettings(
            SettingOptions.UserSettings(shouldEncryptData, userId),
            EncryptedSettingsPlatformParam(platformParam)
        )
        return UserConfigStorageImpl(KaliumPreferencesSettings(settings))
    }
}
