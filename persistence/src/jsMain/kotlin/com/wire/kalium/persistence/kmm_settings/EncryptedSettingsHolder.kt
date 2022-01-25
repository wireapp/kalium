package com.wire.kalium.persistence.kmm_settings

import com.russhwolf.settings.JsSettings
import com.russhwolf.settings.Settings
import org.w3c.dom.Storage

actual class EncryptedSettingsHolder(
    storage: Storage
) {
    actual val encryptedSettings: Settings = JsSettings(storage)
}
