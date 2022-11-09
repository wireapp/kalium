package com.wire.kalium.persistence.kmmSettings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.russhwolf.settings.AndroidSettings
import com.russhwolf.settings.Settings

private fun SettingOptions.keyAlias(): String = when (this) {
    is SettingOptions.AppSettings -> "_app_settings_master_key_"
    is SettingOptions.UserSettings -> "_${this.fileName}_master_key_"
}

@Synchronized
private fun getOrCreateMasterKey(context: Context, keyAlias: String = MasterKey.DEFAULT_MASTER_KEY_ALIAS): MasterKey =
    MasterKey
        .Builder(context, keyAlias)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .setRequestStrongBoxBacked(true)
        .build()

@Synchronized
internal actual fun encryptedSettingsBuilder(
    options: SettingOptions,
    param: EncryptedSettingsPlatformParam
): Settings = AndroidSettings(
    if (options.shouldEncryptData) {
        EncryptedSharedPreferences.create(
            param.appContext,
            options.fileName,
            getOrCreateMasterKey(param.appContext, options.keyAlias()),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } else {
        param.appContext.getSharedPreferences(options.fileName, Context.MODE_PRIVATE)
    }, false
)

internal actual class EncryptedSettingsPlatformParam(val appContext: Context)
