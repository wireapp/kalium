/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.Configuration
import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.Parameters
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig2026
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.daead.DeterministicAeadConfig2026
import com.google.crypto.tink.daead.PredefinedDeterministicAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeystore
import com.wire.kalium.persistence.kaliumLogger
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException

/**
 * Owns the files and the Android Keystore key behind a [TinkEncryptedSharedPreferences] store.
 *
 * Layout, for a legacy `EncryptedSharedPreferences` file named `<name>`:
 * - `<name>_tink`: the encrypted preferences.
 * - `<name>_tink_metadata`: the two Tink keysets, each encrypted by the Keystore master key, and the migration marker.
 * - Keystore alias `_<name>_tink_master_key_`: AES-256-GCM master key, StrongBox-backed when available.
 *
 * The keysets are only ever (re)generated while a migration is in progress, when the store holds no data yet.
 * Once the migration is completed, a missing or unusable key is an error: generating a new one would silently
 * orphan the stored data. Keysets are never stored in cleartext.
 *
 * Not thread-safe ([AndroidKeystore] is not either): callers serialize access.
 */
internal class TinkPreferencesStore(
    private val context: Context,
    legacyFileName: String,
) {
    private val dataFileName = "${legacyFileName}_tink"
    private val metadataFileName = "${legacyFileName}_tink_metadata"
    private val masterKeyAlias = "_${legacyFileName}_tink_master_key_"

    private val data: SharedPreferences
        get() = context.getSharedPreferences(dataFileName, Context.MODE_PRIVATE)

    private val metadata: SharedPreferences
        get() = context.getSharedPreferences(metadataFileName, Context.MODE_PRIVATE)

    fun isMigrationCompleted(): Boolean = metadata.getBoolean(KEY_MIGRATION_COMPLETED, false)

    /**
     * Opens the store of a completed migration.
     * @throws GeneralSecurityException if the master key or a keyset is missing or unusable.
     */
    fun open(): TinkEncryptedSharedPreferences {
        check(isMigrationCompleted()) { "The Tink preferences store is not migrated yet" }
        return createPreferences()
    }

    /**
     * Discards anything left by an unfinished migration and returns a new, empty store with fresh keys.
     * The keysets are read back through the Keystore before returning, so an unusable Keystore fails here,
     * before any data is copied.
     */
    fun createForMigration(): TinkEncryptedSharedPreferences {
        check(!isMigrationCompleted()) { "Refusing to reset a migrated Tink preferences store" }
        discardUnfinishedMigration()
        generateMasterKey()
        val masterAead = AndroidKeystore.getAead(masterKeyAlias)
        val editor = metadata.edit()
        editor.putString(
            KEY_VALUE_KEYSET,
            generateEncryptedKeyset(KEY_VALUE_KEYSET, PredefinedAeadParameters.AES256_GCM, AeadConfig2026.get(), masterAead)
        )
        editor.putString(
            KEY_KEY_KEYSET,
            generateEncryptedKeyset(
                KEY_KEY_KEYSET,
                PredefinedDeterministicAeadParameters.AES256_SIV,
                DeterministicAeadConfig2026.get(),
                masterAead
            )
        )
        if (!editor.commit()) throw IOException("Could not persist the Tink keysets")
        return createPreferences()
    }

    /**
     * Marks the migration as completed. Must only be called once the store holds a verified copy of the data.
     */
    fun markMigrationCompleted() {
        if (!metadata.edit().putBoolean(KEY_MIGRATION_COMPLETED, true).commit()) {
            // The in-memory value is updated even when the disk write fails; undo it so this process stays consistent.
            metadata.edit().remove(KEY_MIGRATION_COMPLETED).commit()
            throw IOException("Could not persist the Tink migration marker")
        }
    }

    /**
     * Removes the files and the master key of an unfinished migration. Never touches a completed one.
     */
    @Suppress("TooGenericExceptionCaught")
    fun discardUnfinishedMigration() {
        if (isMigrationCompleted()) return
        data.edit().clear().commit()
        metadata.edit().clear().commit()
        try {
            AndroidKeystore.deleteKey(masterKeyAlias)
        } catch (e: Exception) {
            // The key is regenerated (overwritten) by the next migration attempt anyway.
            kaliumLogger.w("Could not delete the Tink master key of an unfinished migration", e)
        }
    }

    private fun createPreferences(): TinkEncryptedSharedPreferences {
        val masterAead = AndroidKeystore.getAead(masterKeyAlias)
        val valueAead = readKeyset(KEY_VALUE_KEYSET, AeadConfig2026.get(), masterAead)
            .getPrimitive(AeadConfig2026.get(), Aead::class.java)
        val keyDeterministicAead = readKeyset(KEY_KEY_KEYSET, DeterministicAeadConfig2026.get(), masterAead)
            .getPrimitive(DeterministicAeadConfig2026.get(), DeterministicAead::class.java)
        return TinkEncryptedSharedPreferences(dataFileName, data, valueAead, keyDeterministicAead)
    }

    private fun generateEncryptedKeyset(
        name: String,
        parameters: Parameters,
        configuration: Configuration,
        masterAead: Aead
    ): String {
        val handle = KeysetHandle.generateNew(parameters, configuration)
        val encrypted = TinkProtoKeysetFormat.serializeEncryptedKeyset(handle, masterAead, keysetAssociatedData(name), configuration)
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun readKeyset(name: String, configuration: Configuration, masterAead: Aead): KeysetHandle {
        val encoded = metadata.getString(name, null) ?: throw GeneralSecurityException("Missing Tink keyset $name")
        val encrypted = try {
            Base64.decode(encoded, Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            throw GeneralSecurityException("Malformed Tink keyset $name", e)
        }
        return TinkProtoKeysetFormat.parseEncryptedKeyset(encrypted, masterAead, keysetAssociatedData(name), configuration)
    }

    private fun keysetAssociatedData(name: String) = "$metadataFileName/$name".toByteArray(Charsets.UTF_8)

    /**
     * Generates (or overwrites) the master key, in StrongBox when the device has one, otherwise in the TEE.
     */
    private fun generateMasterKey() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                AndroidKeystore.generateNewKeyWithSpec(masterKeySpec(strongBoxBacked = true))
                return
            } catch (e: ProviderException) {
                // Includes StrongBoxUnavailableException.
                kaliumLogger.i("StrongBox unavailable for the Tink master key, using the TEE: ${e.javaClass.simpleName}")
            } catch (e: GeneralSecurityException) {
                kaliumLogger.i("StrongBox unavailable for the Tink master key, using the TEE: ${e.javaClass.simpleName}")
            }
        }
        AndroidKeystore.generateNewKeyWithSpec(masterKeySpec(strongBoxBacked = false))
    }

    private fun masterKeySpec(strongBoxBacked: Boolean): KeyGenParameterSpec =
        KeyGenParameterSpec.Builder(masterKeyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(MASTER_KEY_SIZE_BITS)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .apply { if (strongBoxBacked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }
            .build()

    private companion object {
        const val KEY_VALUE_KEYSET = "value_keyset"
        const val KEY_KEY_KEYSET = "key_keyset"
        const val KEY_MIGRATION_COMPLETED = "migration_completed"
        const val MASTER_KEY_SIZE_BITS = 256
    }
}
