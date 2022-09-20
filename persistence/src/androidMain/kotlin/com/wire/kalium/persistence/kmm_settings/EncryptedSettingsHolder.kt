package com.wire.kalium.persistence.kmm_settings

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
                EncryptedSharedPrefUtil.getOrCreateMasterKey(applicationContext),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } else {
            applicationContext.getSharedPreferences(options.fileName, Context.MODE_PRIVATE)
        }, false
    )
}

private object EncryptedSharedPrefUtil {
    @Synchronized
    fun getOrCreateMasterKey(context: Context): MasterKey =
        MasterKey
            .Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setRequestStrongBoxBacked(true)
            .build()

}
