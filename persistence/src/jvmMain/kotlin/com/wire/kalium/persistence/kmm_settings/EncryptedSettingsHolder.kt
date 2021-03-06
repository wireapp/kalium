package com.wire.kalium.persistence.kmm_settings

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.JvmPreferencesSettings
import com.russhwolf.settings.Settings
import com.wire.kalium.PackagePathProvider
import java.util.prefs.Preferences

/**
 * the java implementation is not yet encrypted
 */

@OptIn(ExperimentalSettingsImplementation::class)
actual class EncryptedSettingsHolder(
    options: SettingOptions
) {
    private val preferences: Preferences = Preferences.userNodeForPackage(PackagePathProvider::class.java).node(options.fileName)
    // TODO(jvm): JvmPreferencesSettings is not encrypted
    actual val encryptedSettings: Settings = JvmPreferencesSettings(preferences)
}
