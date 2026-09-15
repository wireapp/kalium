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

import com.wire.kalium.persistence.util.FileNameUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom

/** Keeps the master key of the settings files outside of them, in a system key store. */
internal interface MasterKeyStore {

    /** Written to the key file, so a key file from another key store is recognised. */
    val name: String

    /** Keeps [key] and returns the reference to write into the key file. */
    fun store(key: ByteArray): String

    /** Returns the key behind [reference]; throws [SettingsEncryptionException] if it's gone or the store refuses. */
    fun load(reference: String): ByteArray

    /**
     * A better reference for [key], which [reference] points to, or null to keep [reference]. Called after every
     * successful [load], so a key that could only be protected weakly when it was stored is protected better once it can.
     */
    fun upgrade(reference: String, key: ByteArray): String? = null
}

/** The key store of this system; throws [SettingsEncryptionException] where there is none. */
internal fun platformMasterKeyStore(): MasterKeyStore {
    val osName = System.getProperty("os.name").orEmpty()
    return when {
        osName.startsWith("Mac", ignoreCase = true) -> MacKeychainMasterKeyStore()
        osName.startsWith("Linux", ignoreCase = true) -> LibsecretMasterKeyStore()
        osName.startsWith("Windows", ignoreCase = true) -> WindowsDpapiNgMasterKeyStore()
        else -> throw SettingsEncryptionException("Encrypted settings need a system key store, and there is no supported one on $osName")
    }
}

/**
 * The master keys that encrypt the settings files, one per settings folder.
 *
 * The key itself stays in a [MasterKeyStore]; a small key file next to the settings names the key
 * store and the entry. When the store can protect a loaded key better than when it was stored, the
 * key file gets the new reference (see [MasterKeyStore.upgrade]).
 *
 * Settings written before the encryption are plaintext. When the key of a folder is created, all of
 * them are encrypted with it, and only then the key file gets its final name; after an interruption
 * the next start continues from the pending key file. Once the key file exists, a plaintext settings
 * file is an error. So is an encrypted one without key file: those settings can't be read anymore,
 * and a new key would hide that.
 */
internal class SettingsMasterKeys(private val keyStore: () -> MasterKeyStore) {

    private val keys = HashMap<String, ByteArray>()

    @Synchronized
    fun forSettings(settingsFile: File): ByteArray {
        val folder = settingsFile.absoluteFile.parentFile
        return keys.getOrPut(folder.path) { loadOrCreate(folder) }
    }

    private fun loadOrCreate(folder: File): ByteArray {
        val keyFile = File(folder, KEY_FILE_NAME)
        if (keyFile.exists()) return load(keyFile)

        val pendingKeyFile = File(folder, PENDING_KEY_FILE_NAME)
        val key = if (pendingKeyFile.exists()) load(pendingKeyFile) else create(folder, pendingKeyFile)
        val cipher = SettingsFileCipher(key)
        settingsFiles(folder).filterNot { SettingsFileCipher.isEncrypted(it) }
            .forEach { writeAtomically(it, cipher.encrypt(it.readBytes())) }
        retryWhileLocked { Files.move(pendingKeyFile.toPath(), keyFile.toPath(), StandardCopyOption.ATOMIC_MOVE) }
        return key
    }

    private fun create(folder: File, pendingKeyFile: File): ByteArray {
        settingsFiles(folder).firstOrNull { SettingsFileCipher.isEncrypted(it) }?.let {
            throw SettingsEncryptionException("The master key of ${it.name} is missing, so the settings can't be decrypted")
        }
        val store = keyStore()
        val key = ByteArray(KEY_SIZE_BYTES).also(random::nextBytes)
        writeAtomically(pendingKeyFile, keyFileContent(store, store.store(key)))
        return key
    }

    /** The settings files in [folder] that hold settings; empty ones hold none. */
    private fun settingsFiles(folder: File): List<File> =
        folder.listFiles { file -> file.isFile && file.length() > 0 && FileNameUtil.isPrefFile(file.name) }.orEmpty().toList()

    private fun keyFileContent(store: MasterKeyStore, reference: String): ByteArray =
        listOf(HEADER, store.name, reference).joinToString(separator = "\n", postfix = "\n").encodeToByteArray()

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
        val key = store.load(lines[2])
        store.upgrade(lines[2], key)?.let { writeAtomically(keyFile, keyFileContent(store, it)) }
        return key
    }

    companion object {
        const val KEY_FILE_NAME = "settings-master-key"
        const val PENDING_KEY_FILE_NAME = "$KEY_FILE_NAME.pending"
        private const val HEADER = "kalium-settings-master-key-v1"
        private const val KEY_FILE_LINES = 3
        private const val KEY_SIZE_BYTES = 32
        private val random = SecureRandom()

        /** The master keys of this process, kept in the system's own key store. */
        val platform = SettingsMasterKeys(::platformMasterKeyStore)
    }
}
