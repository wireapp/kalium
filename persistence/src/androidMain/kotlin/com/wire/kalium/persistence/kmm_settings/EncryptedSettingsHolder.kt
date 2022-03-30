package com.wire.kalium.persistence.kmm_settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.russhwolf.settings.AndroidSettings
import com.russhwolf.settings.Settings

actual class EncryptedSettingsHolder(
    applicationContext: Context,
    options: SettingOptions,
    masterKeyAlias: String = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
) {
    actual val encryptedSettings: Settings = AndroidSettings(
        EncryptedSharedPreferences.create(
            options.fileName,
            masterKeyAlias,
            applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ), false
    )
}
