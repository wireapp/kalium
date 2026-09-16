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
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts a whole settings file with AES-256-GCM, keys and values alike.
 *
 * File layout: a readable header line, the 12-byte nonce, then the encrypted properties and the
 * GCM tag. The header is authenticated as well. A wrong key, a changed byte or a plaintext file
 * makes [load] throw [SettingsEncryptionException] instead of starting with empty settings, which
 * would lose the keys of the CoreCrypto keystores.
 */
internal class SettingsFileCipher(key: ByteArray) {

    private val key = SecretKeySpec(
        key.also { require(it.size == KEY_SIZE_BYTES) { "Settings keys must have $KEY_SIZE_BYTES bytes, got ${it.size}" } },
        "AES"
    )

    fun encrypt(content: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_SIZE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, nonce))
            updateAAD(HEADER)
        }
        return HEADER + nonce + cipher.doFinal(content)
    }

    /** Reads [file]; a missing or empty file means no settings stored yet. */
    fun load(file: File): Properties {
        val bytes = if (file.exists()) file.readBytes() else ByteArray(0)
        if (bytes.isEmpty()) return Properties()

        if (bytes.size <= HEADER.size + NONCE_SIZE_BYTES || !bytes.copyOf(HEADER.size).contentEquals(HEADER)) {
            throw SettingsEncryptionException(
                "Settings file ${file.name} is not encrypted, although its master key exists; remove the file to start over."
            )
        }
        val nonce = bytes.copyOfRange(HEADER.size, HEADER.size + NONCE_SIZE_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, nonce))
            updateAAD(HEADER)
        }
        val content = try {
            cipher.doFinal(bytes, HEADER.size + NONCE_SIZE_BYTES, bytes.size - HEADER.size - NONCE_SIZE_BYTES)
        } catch (exception: GeneralSecurityException) {
            throw SettingsEncryptionException("Settings file ${file.name} can't be decrypted: wrong key or changed file", exception)
        }
        return Properties().apply { load(content.inputStream()) }
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BYTES = 32
        private const val NONCE_SIZE_BYTES = 12
        private const val TAG_SIZE_BITS = 128
        private val HEADER = "kalium-encrypted-settings-v1\n".encodeToByteArray()
        private val random = SecureRandom()

        /** Whether [file] starts with the header of an encrypted settings file. */
        fun isEncrypted(file: File): Boolean = file.inputStream().use { it.readNBytes(HEADER.size) }.contentEquals(HEADER)
    }
}
