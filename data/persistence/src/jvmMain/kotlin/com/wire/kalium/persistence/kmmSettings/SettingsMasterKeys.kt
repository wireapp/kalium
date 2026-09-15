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

import java.io.File
import java.security.SecureRandom

/** Keeps the master key of the settings files outside of them, in a system key store. */
internal interface MasterKeyStore {

    /** Written to the key file, so a key file from another key store is recognised. */
    val name: String

    /** Keeps [key] and returns the reference to write into the key file. */
    fun store(key: ByteArray): String

    /** Returns the key behind [reference]; throws [SettingsEncryptionException] if it's gone or the store refuses. */
    fun load(reference: String): ByteArray
}

/** The key store of this system; throws [SettingsEncryptionException] where there is none. */
internal fun platformMasterKeyStore(): MasterKeyStore {
    val osName = System.getProperty("os.name").orEmpty()
    return when {
        osName.startsWith("Mac", ignoreCase = true) -> MacKeychainMasterKeyStore()
        else -> throw SettingsEncryptionException("Encrypted settings need a system key store, and there is no supported one on $osName")
    }
}

/**
 * The master keys that encrypt the settings files, one per settings folder.
 *
 * The key itself stays in a [MasterKeyStore]; a small key file next to the settings names the key
 * store and the entry. A new key is only created while the settings file is still empty. If the key
 * file is missing next to existing settings, those settings can't be read anymore, and a new key
 * would hide that.
 */
internal class SettingsMasterKeys(private val keyStore: () -> MasterKeyStore) {

    private val keys = HashMap<String, ByteArray>()

    @Synchronized
    fun forSettings(settingsFile: File): ByteArray =
        keys.getOrPut(settingsFile.absoluteFile.parentFile.path) { loadOrCreate(settingsFile) }

    private fun loadOrCreate(settingsFile: File): ByteArray {
        val keyFile = File(settingsFile.absoluteFile.parentFile, KEY_FILE_NAME)
        if (keyFile.exists()) return load(keyFile)

        if (settingsFile.length() > 0) {
            throw SettingsEncryptionException("The master key of ${settingsFile.name} is missing, so the settings can't be decrypted")
        }
        val store = keyStore()
        val key = ByteArray(KEY_SIZE_BYTES).also(random::nextBytes)
        val reference = store.store(key)
        writeAtomically(keyFile, listOf(HEADER, store.name, reference).joinToString(separator = "\n", postfix = "\n").encodeToByteArray())
        return key
    }

    private fun load(keyFile: File): ByteArray {
        val lines = keyFile.readLines()
        if (lines.size < KEY_FILE_LINES || lines[0] != HEADER) {
            throw SettingsEncryptionException("${keyFile.name} is not a settings master key file")
        }
        val store = keyStore()
        if (lines[1] != store.name) {
            throw SettingsEncryptionException(
                "The settings master key belongs to the ${lines[1]} key store, but this system uses ${store.name}"
            )
        }
        return store.load(lines[2])
    }

    companion object {
        const val KEY_FILE_NAME = "settings-master-key"
        private const val HEADER = "kalium-settings-master-key-v1"
        private const val KEY_FILE_LINES = 3
        private const val KEY_SIZE_BYTES = 32
        private val random = SecureRandom()

        /** The master keys of this process, kept in the system's own key store. */
        val platform = SettingsMasterKeys(::platformMasterKeyStore)
    }
}
