package com.wire.kalium.persistence.kmmSettings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.russhwolf.settings.AndroidSettings
import com.russhwolf.settings.Settings

actual class EncryptedSettingsHolder(
    applicationContext: Context,
    options: SettingOptions
) {
    @get:Synchronized
    actual val encryptedSettings: Settings = AndroidSettings(
        if (options.shouldEncryptData) {
            EncryptedSharedPreferences.create(
                applicationContext,
                options.fileName,
                EncryptedSharedPrefUtil.getOrCreateMasterKey(applicationContext, options.keyAlias()),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } else {
            applicationContext.getSharedPreferences(options.fileName, Context.MODE_PRIVATE)
        }, false
    )
}

private fun SettingOptions.keyAlias(): String = when (this) {
    is SettingOptions.AppSettings -> "_app_settings_master_key_"
    is SettingOptions.UserSettings -> "_${this.fileName}_master_key_"
}

private object EncryptedSharedPrefUtil {
    @Synchronized
    fun getOrCreateMasterKey(context: Context, keyAlias: String = MasterKey.DEFAULT_MASTER_KEY_ALIAS): MasterKey =
        MasterKey
            .Builder(context, keyAlias)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setRequestStrongBoxBacked(true)
            .build()
}
