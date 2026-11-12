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
import com.wire.kalium.persistence.kaliumLogger
import kotlinx.coroutines.delay
import java.io.IOException
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
    param: EncryptedSettingsPlatformParam
): SharedPreferences = synchronized(lock) {
    if (options.shouldEncryptData) {
        encryptedSharedPref(options, param, false)
    } else {
        param.appContext.getSharedPreferences(options.fileName, Context.MODE_PRIVATE)
    }
}

/**
 * Process-wide instances of the migrating preferences, so every caller of a file uses the same store, even if a
 * migration failed and that file stays on the legacy store for the rest of this process.
 */
private val migratingSharedPreferences = mutableMapOf<String, SharedPreferences>()

/**
 * Builds the encrypted preferences for [options]: the global ones (auth tokens and database passphrases) or the
 * per-user ones.
 *
 * With [useTinkEncryptedPreferences], the legacy `EncryptedSharedPreferences` file is migrated to a
 * [TinkEncryptedSharedPreferences] store. The migration never modifies the legacy file:
 * 1. the legacy data is copied to a fresh Tink store and committed,
 * 2. the copy is read back and compared with the legacy data,
 * 3. only then the migration is marked as completed.
 * If any step fails, the unfinished Tink store is discarded, the legacy store is used for this process, and the
 * migration is retried on the next start.
 *
 * Once a migration completed, the Tink store is used even if [useTinkEncryptedPreferences] is turned off again:
 * the legacy file is stale from that point on, and switching back to it would lose the newer values.
 *
 * The legacy file is kept as is. Deleting it is left to a later release, once the migration is proven in production.
 */
internal fun buildMigratingSharedPreferences(
    options: SettingOptions,
    param: EncryptedSettingsPlatformParam,
    useTinkEncryptedPreferences: Boolean
): SharedPreferences = synchronized(lock) {
    if (!options.shouldEncryptData) return@synchronized buildSharedPreferences(options, param)
    migratingSharedPreferences.getOrPut(options.fileName) {
        val tinkStore = TinkPreferencesStore(param.appContext, options.fileName)
        when {
            tinkStore.isMigrationCompleted() -> openTinkStore(tinkStore, isRetry = false).also {
                // Warning level so it shows up with the minimal startup logging. Logged once per file per process.
                kaliumLogger.w("Preferences already migrated, using the Tink store")
            }
            useTinkEncryptedPreferences -> {
                val legacy = encryptedSharedPref(options, param, false)
                migrateToTink(legacy, tinkStore) ?: legacy
            }

            else -> encryptedSharedPref(options, param, false)
        }
    }
}

@Suppress("TooGenericExceptionCaught")
private fun openTinkStore(tinkStore: TinkPreferencesStore, isRetry: Boolean): SharedPreferences = try {
    tinkStore.open()
} catch (e: Exception) {
    // Same policy as the legacy store: one retry for transient Keystore failures, then fail loudly.
    // Never fall back to the legacy file here: it is stale once the migration completed.
    if (isRetry) {
        throw e
    } else runBlocking {
        delay(RETRY_DELAY)
        openTinkStore(tinkStore, true)
    }
}

/**
 * Copies [legacy] into a fresh [TinkPreferencesStore] and verifies it.
 * @return the migrated preferences, or null if the migration failed and [legacy] must still be used.
 */
@Suppress("TooGenericExceptionCaught")
internal fun migrateToTink(legacy: SharedPreferences, tinkStore: TinkPreferencesStore): SharedPreferences? = try {
    val legacyEntries = legacy.all
    val tinkPreferences = tinkStore.createForMigration()
    val editor = tinkPreferences.edit().clear()
    legacyEntries.forEach { (key, value) -> editor.putAny(key, value) }
    if (!editor.commit()) throw IOException("Could not persist the migrated preferences")
    check(tinkPreferences.all == legacyEntries) { "The migrated preferences do not match the legacy ones" }
    tinkStore.markMigrationCompleted()
    // Logged as a warning so it shows up with the minimal startup logging, which is WARN and above.
    kaliumLogger.w("Migrated ${legacyEntries.size} preferences to the Tink store")
    tinkPreferences
} catch (e: Exception) {
    kaliumLogger.w("Migration of preferences to the Tink store failed, keeping the legacy store", e)
    try {
        tinkStore.discardUnfinishedMigration()
    } catch (cleanupError: Exception) {
        kaliumLogger.w("Could not discard the unfinished Tink migration", cleanupError)
    }
    null
}

private fun SharedPreferences.Editor.putAny(key: String, value: Any?) {
    when (value) {
        is String -> putString(key, value)
        is Set<*> -> putStringSet(key, value.mapTo(mutableSetOf()) { it as String })
        is Int -> putInt(key, value)
        is Long -> putLong(key, value)
        is Float -> putFloat(key, value)
        is Boolean -> putBoolean(key, value)
        else -> throw IllegalArgumentException("Unsupported preference type for migration")
    }
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
