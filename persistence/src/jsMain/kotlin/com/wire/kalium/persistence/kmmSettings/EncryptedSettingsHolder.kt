package com.wire.kalium.persistence.kmmSettings

import com.russhwolf.settings.JsSettings
import com.russhwolf.settings.Settings
import org.w3c.dom.Storage

actual class EncryptedSettingsHolder(
    storage: Storage
) {
    actual val encryptedSettings: Settings = JsSettings(storage)
}
