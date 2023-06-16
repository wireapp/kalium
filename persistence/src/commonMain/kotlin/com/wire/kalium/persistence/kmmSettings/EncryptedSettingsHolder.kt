/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.russhwolf.settings.Settings
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.util.FileNameUtil

internal sealed class SettingOptions {
    abstract val fileName: String
    abstract val shouldEncryptData: Boolean

    class AppSettings(override val shouldEncryptData: Boolean = true) : SettingOptions() {
        override val fileName: String = FileNameUtil.appPrefFile()
    }

    class UserSettings(override val shouldEncryptData: Boolean = true, userIDEntity: UserIDEntity) : SettingOptions() {
        override val fileName: String = FileNameUtil.userPrefFile(userIDEntity)
    }
}

internal expect object EncryptedSettingsBuilder {
    fun build(options: SettingOptions, param: EncryptedSettingsPlatformParam): Settings
}
internal expect class EncryptedSettingsPlatformParam
