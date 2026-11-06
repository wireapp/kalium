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
import com.wire.kalium.persistence.kaliumLogger
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
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
): Settings = SharedPreferencesSettings(buildSharedPreferences(options, param), false)

internal fun buildSharedPreferences(
    options: SettingOptions,
    param: EncryptedSettingsPlatformParam,
    masterKeyBuilder: (Context, String) -> MasterKey = { context, alias -> getOrCreateMasterKey(context, alias) },
    preferencesBuilder: (Context, String, MasterKey) -> SharedPreferences = ::createEncryptedSharedPreferences
): SharedPreferences = synchronized(lock) {
    if (options.shouldEncryptData) {
        val masterKey = masterKeyBuilder(param.appContext, options.keyAlias())
        encryptedSharedPref {
            preferencesBuilder(param.appContext, options.fileName, masterKey)
        }
    } else {
        param.appContext.getSharedPreferences(options.fileName, Context.MODE_PRIVATE)
    }
}

@Suppress("TooGenericExceptionCaught")
internal fun getOrCreateMasterKey(
    context: Context,
    keyAlias: String,
    strongBoxAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE),
    keyBuilder: (Boolean) -> MasterKey = { requestStrongBox ->
        MasterKey.Builder(context, keyAlias)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setRequestStrongBoxBacked(requestStrongBox)
            .build()
    }
): MasterKey = try {
    buildMasterKeyWithFallback(strongBoxAvailable, keyBuilder)
} catch (failure: Exception) {
    if (!failure.isKeyInitializationFailure()) throw failure
    throw MasterKeyInitializationException(failure)
}

private fun Exception.isKeyInitializationFailure(): Boolean =
    this is GeneralSecurityException || this is ProviderException || this is IOException

@Suppress("TooGenericExceptionCaught")
private fun buildMasterKeyWithFallback(strongBoxAvailable: Boolean, keyBuilder: (Boolean) -> MasterKey): MasterKey {
    if (!strongBoxAvailable) return keyBuilder(false)

    return try {
        keyBuilder(true)
    } catch (strongBoxFailure: Exception) {
        if (!strongBoxFailure.isKeyInitializationFailure()) {
            throw strongBoxFailure
        }
        // Provider messages can contain aliases or other identifying information.
        kaliumLogger.w("StrongBox master-key initialization failed; retrying with regular AndroidKeyStore")
        try {
            keyBuilder(false)
        } catch (fallbackFailure: Exception) {
            if (fallbackFailure !== strongBoxFailure) fallbackFailure.addSuppressed(strongBoxFailure)
            throw fallbackFailure
        }
    }
}

private fun createEncryptedSharedPreferences(context: Context, fileName: String, masterKey: MasterKey): SharedPreferences =
    EncryptedSharedPreferences.create(
        context,
        fileName,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

@Suppress("TooGenericExceptionCaught")
private fun encryptedSharedPref(createPreferences: () -> SharedPreferences): SharedPreferences = try {
    createPreferences()
} catch (_: Exception) {
    runBlocking {
        delay(RETRY_DELAY)
        createPreferences()
    }
}

internal actual class EncryptedSettingsPlatformParam(val appContext: Context)

private const val RETRY_DELAY = 200L
