package com.wire.kalium.persistence.kmm_settings

import com.russhwolf.settings.Settings

expect class EncryptedSettingsHolder {
    val encryptedSettings: Settings
}
