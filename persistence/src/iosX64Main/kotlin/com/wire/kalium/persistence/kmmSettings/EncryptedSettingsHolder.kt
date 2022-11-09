package com.wire.kalium.persistence.kmmSettings

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.Settings

@OptIn(ExperimentalSettingsImplementation::class)
actual fun encryptedSettingsBuilder(
    options: SettingOptions,
    param: EncryptedSettingsPlatformParam
): Settings = KeychainSettings(param.serviceName)

actual class EncryptedSettingsPlatformParam(val serviceName: String)
