/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.kmmSettings

import com.wire.kalium.persistence.atomicWrite
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Protects AndroidX Preferences protobuf bytes without changing their serialization. */
class DesktopPreferenceCipher private constructor(file: Path, key: ByteArray) {
    private val key = SecretKeySpec(key, "AES")
    private val identity = ("Wire Preferences v1/" + file.toFile().canonicalPath).encodeToByteArray()

    fun encrypt(bytes: ByteArray): ByteArray = encrypt(bytes, false)

    fun encryptVerifier(bytes: ByteArray): ByteArray = encrypt(bytes, true)

    private fun encrypt(bytes: ByteArray, verifier: Boolean): ByteArray {
        val nonce = ByteArray(12).also(random::nextBytes)
        return nonce + cipher(Cipher.ENCRYPT_MODE, nonce, verifier).doFinal(bytes)
    }

    fun decrypt(bytes: ByteArray): ByteArray = decrypt(bytes, false)

    fun decryptVerifier(bytes: ByteArray): ByteArray = decrypt(bytes, true)

    private fun decrypt(bytes: ByteArray, verifier: Boolean): ByteArray {
        check(bytes.size >= 28) { "Truncated Preferences ciphertext" }
        return cipher(Cipher.DECRYPT_MODE, bytes.copyOfRange(0, 12), verifier).doFinal(bytes, 12, bytes.size - 12)
    }

    private fun cipher(mode: Int, nonce: ByteArray, verifier: Boolean) = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(mode, key, GCMParameterSpec(128, nonce))
        updateAAD(identity)
        if (verifier) updateAAD(byteArrayOf(0) + "App Lock verifier v1".encodeToByteArray())
    }

    companion object {
        private val random = SecureRandom()

        @Synchronized
        fun open(legacy: Path): DesktopPreferenceCipher {
            val file = legacy.resolveSibling("${legacy.fileName}.encrypted")
            val lock = legacy.resolveSibling("${legacy.fileName}.lock")
            return FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
                channel.lock().use {
                    check(!Files.isSymbolicLink(legacy) && !Files.isSymbolicLink(file))
                    val missing = Files.notExists(file, LinkOption.NOFOLLOW_LINKS)
                    val key = DesktopSettingsKey(file).load(allowCreation = missing)
                    val cipher = try { DesktopPreferenceCipher(file, key) } finally { key.fill(0) }
                    if (!Files.notExists(legacy)) {
                        val original = Files.readAllBytes(legacy)
                        try {
                            if (missing) atomicWrite(file, cipher.encrypt(original))
                            val verified = cipher.decrypt(Files.readAllBytes(file))
                            try {
                                check(verified.contentEquals(original)) { "Divergent Preferences files" }
                            } finally {
                                verified.fill(0)
                            }
                            Files.delete(legacy)
                        } finally {
                            original.fill(0)
                        }
                    }
                    cipher
                }
            }
        }
    }
}
