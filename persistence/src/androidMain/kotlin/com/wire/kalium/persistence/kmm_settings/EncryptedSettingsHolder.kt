package com.wire.kalium.persistence.kmm_settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.russhwolf.settings.AndroidSettings
import com.russhwolf.settings.Settings

actual class EncryptedSettingsHolder(
    private val applicationContext: Context,
    options: SettingOptions
) {
    private fun getOrCreateMasterKey(): MasterKey = MasterKey
        .Builder(applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .setRequestStrongBoxBacked(true)
        .build()

    actual val encryptedSettings: Settings = AndroidSettings(
        EncryptedSharedPreferences.create(
            applicationContext,
            options.fileName,
            getOrCreateMasterKey(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ), false
    )
}
