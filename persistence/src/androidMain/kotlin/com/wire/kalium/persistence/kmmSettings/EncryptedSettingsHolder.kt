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
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

private fun SettingOptions.keyAlias(): String = when (this) {
    is SettingOptions.AppSettings -> "_app_settings_master_key_"
    is SettingOptions.UserSettings -> "_${this.fileName}_master_key_"
}

private val lock = Object()

internal actual fun buildSettings(
    options: SettingOptions,
    param: EncryptedSettingsPlatformParam
): Settings = synchronized(lock) {
    val settings = if (options.shouldEncryptData) {
        encryptedSharedPref(options, param, false)
    } else {
        param.appContext.getSharedPreferences(options.fileName, Context.MODE_PRIVATE)
    }
    SharedPreferencesSettings(settings, false)
}

private fun getOrCreateMasterKey(
    context: Context,
    keyAlias: String
): MasterKey =
    MasterKey
        .Builder(context, keyAlias)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .setRequestStrongBoxBacked(true)
        .build()

private fun encryptedSharedPref(
    options: SettingOptions,
    param: EncryptedSettingsPlatformParam,
    isRetry: Boolean
): SharedPreferences {
    @Suppress("TooGenericExceptionCaught")
    return try {
        val masterKey = getOrCreateMasterKey(param.appContext, options.keyAlias())
        EncryptedSharedPreferences.create(
            param.appContext,
            options.fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        if (isRetry) {
            throw e
        } else runBlocking {
            delay(RETRY_DELAY)
            encryptedSharedPref(options, param, true)
        }
    }
}

internal actual class EncryptedSettingsPlatformParam(val appContext: Context)

private const val RETRY_DELAY = 200L
