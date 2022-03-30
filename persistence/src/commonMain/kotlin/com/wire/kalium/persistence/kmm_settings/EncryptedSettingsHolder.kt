package com.wire.kalium.persistence.kmm_settings

import com.russhwolf.settings.Settings
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.util.FileNameUtil

sealed class SettingOptions {
    abstract val fileName: String
    object AppSettings: SettingOptions() {
        override val fileName: String = FileNameUtil.appPrefFile()
    }
    class UserSettings(userIDEntity: UserIDEntity): SettingOptions() {
        override val fileName: String = FileNameUtil.userPrefFile(userIDEntity)
    }
}

expect class EncryptedSettingsHolder {
    val encryptedSettings: Settings
}
