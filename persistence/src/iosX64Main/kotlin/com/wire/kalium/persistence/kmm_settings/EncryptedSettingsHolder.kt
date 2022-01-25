package com.wire.kalium.persistence.kmm_settings

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.Settings

@OptIn(ExperimentalSettingsImplementation::class)
actual class EncryptedSettingsHolder(
    serviceName: String
) {
    actual val encryptedSettings: Settings = KeychainSettings(serviceName)
}
