package com.wire.kalium.persistence.kmmSettings

import com.russhwolf.settings.Settings
import com.russhwolf.settings.StorageSettings
import org.w3c.dom.Storage

internal actual fun encryptedSettingsBuilder(
    options: SettingOptions,
    param: EncryptedSettingsPlatformParam
): Settings = StorageSettings(param.storage)

internal actual class EncryptedSettingsPlatformParam(val storage: Storage)
