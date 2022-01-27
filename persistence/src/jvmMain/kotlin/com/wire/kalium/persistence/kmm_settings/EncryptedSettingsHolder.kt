package com.wire.kalium.persistence.kmm_settings

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.JvmPreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences

/**
 * the java implementation is not yet encrypted
 */

@OptIn(ExperimentalSettingsImplementation::class)
actual class EncryptedSettingsHolder(
    preferencesFilePath: String
) {
    private val preferences: Preferences = Preferences.userRoot().node(preferencesFilePath)
    actual val encryptedSettings: Settings = JvmPreferencesSettings(preferences)
}
