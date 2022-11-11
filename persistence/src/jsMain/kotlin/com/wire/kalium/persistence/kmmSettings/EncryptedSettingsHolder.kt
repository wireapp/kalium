package com.wire.kalium.persistence.kmmSettings

import com.russhwolf.settings.JsSettings
import com.russhwolf.settings.Settings
import org.w3c.dom.Storage

internal actual fun encryptedSettingsBuilder(
    options: SettingOptions,
    param: EncryptedSettingsPlatformParam
): Settings = JsSettings(param.storage)

internal actual class EncryptedSettingsPlatformParam(val storage: Storage)
