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

internal expect fun encryptedSettingsBuilder(options: SettingOptions, param: EncryptedSettingsPlatformParam): Settings

internal expect class EncryptedSettingsPlatformParam
