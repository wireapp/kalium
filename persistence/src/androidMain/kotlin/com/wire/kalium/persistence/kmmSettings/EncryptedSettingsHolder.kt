/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import java.security.InvalidKeyException

private fun SettingOptions.keyAlias(): String = when (this) {
    is SettingOptions.AppSettings -> "_app_settings_master_key_"
    is SettingOptions.UserSettings -> "_${this.fileName}_master_key_"
}

internal actual object EncryptedSettingsBuilder {
    private var retry: Boolean = true

    private fun getOrCreateMasterKey(context: Context, keyAlias: String = MasterKey.DEFAULT_MASTER_KEY_ALIAS): MasterKey =
        MasterKey
            .Builder(context, keyAlias)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setRequestStrongBoxBacked(true)
            .build()

    actual fun build(
        options: SettingOptions,
        param: EncryptedSettingsPlatformParam
    ): Settings = synchronized(this) {
        try {
            SharedPreferencesSettings(
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
        } catch (exception: Exception) {
            exception.printStackTrace()
            if (exception is InvalidKeyException) {
                if (retry) {
                    retry = false
                    build(options, param)
                } else {
                    throw exception
                }
            } else {
                throw exception
            }
        }
    }
}

internal actual class EncryptedSettingsPlatformParam(val appContext: Context)
