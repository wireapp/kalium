package com.wire.kalium.persistence.kmmSettings

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.Settings

@OptIn(ExperimentalSettingsImplementation::class)
internal actual fun encryptedSettingsBuilder(
    options: SettingOptions,
    param: EncryptedSettingsPlatformParam
): Settings = KeychainSettings(param.serviceName)

internal actual class EncryptedSettingsPlatformParam(val serviceName: String)
