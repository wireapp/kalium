/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.persistence.kmmSettings

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import java.security.GeneralSecurityException
import java.security.KeyStore

private fun SettingOptions.keyAlias(): String = when (this) {
    is SettingOptions.AppSettings -> "_app_settings_master_key_"
    is SettingOptions.UserSettings -> "_${this.fileName}_master_key_"
}

private val lock = Any()

internal actual fun buildSettings(
    options: SettingOptions,
    param: EncryptedSettingsPlatformParam
): Settings = synchronized(lock) {
    try {
        safeBuildSettings(options, param)
    } catch (e: GeneralSecurityException) {
        // Recover corrupted keystore or preferences
        recoverFromInvalidKey(options, param)

        try {
            // Retry once after recovery
            safeBuildSettings(options, param)
        } catch (_: Exception) {
            // Final fallback: unencrypted SharedPreferences
            SharedPreferencesSettings(
                param.appContext.getSharedPreferences(
                    options.fileName,
                    Context.MODE_PRIVATE
                ),
                false
            )
        }
    }
}

private fun safeBuildSettings(
    options: SettingOptions,
    param: EncryptedSettingsPlatformParam
): Settings {
    val prefs = if (options.shouldEncryptData) {
        encryptedSharedPref(options, param)
    } else {
        param.appContext.getSharedPreferences(
            options.fileName,
            Context.MODE_PRIVATE
        )
    }

    return SharedPreferencesSettings(prefs, false)
}

private fun encryptedSharedPref(
    options: SettingOptions,
    param: EncryptedSettingsPlatformParam
): SharedPreferences {
    val masterKey = getOrCreateMasterKey(
        param.appContext,
        options.keyAlias()
    )

    return EncryptedSharedPreferences.create(
        param.appContext,
        options.fileName,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

private fun getOrCreateMasterKey(
    context: Context,
    keyAlias: String
): MasterKey {
    val useStrongBox = supportsStrongBox(context)

    return MasterKey.Builder(context, keyAlias)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .setRequestStrongBoxBacked(useStrongBox)
        .build()
}

private fun supportsStrongBox(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)


private fun recoverFromInvalidKey(
    options: SettingOptions,
    param: EncryptedSettingsPlatformParam
) {
    // Delete corrupted SharedPreferences
    try {
        param.appContext.deleteSharedPreferences(options.fileName)
    } catch (_: Exception) {
        // ignored
    }

    // Delete corrupted keystore entry
    try {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
        val alias = options.keyAlias()
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    } catch (_: Exception) {
        // ignored
    }
}

internal actual class EncryptedSettingsPlatformParam(
    val appContext: Context
)
