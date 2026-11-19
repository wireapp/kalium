/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.kmmSettings

import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.Settings
import com.wire.kalium.persistence.atomicWrite
import java.io.ByteArrayOutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class DesktopProtectedSettings(private val legacy: Path, private val identity: Path = legacy) : Settings {
    private val file = identity.resolveSibling("${identity.fileName}.encrypted")
    private val lock = identity.resolveSibling("${identity.fileName}.lock")
    private val key: ByteArray
    private val random = SecureRandom()
    private val aad = ("Wire settings v1/" + identity.toFile().canonicalPath).encodeToByteArray()

    init {
        Files.createDirectories(file.parent)
        key = locked {
            val encryptedExists = !Files.notExists(file)
            val key = DesktopSettingsKey(identity).load(allowCreation = !encryptedExists)
            if (!encryptedExists) {
                val original = Properties()
                if (!Files.notExists(legacy)) Files.newInputStream(legacy).use(original::load)
                save(original, key)
                check(load(key) == original) { "Settings migration verification failed" }
            } else {
                val current = load(key)
                if (!Files.notExists(legacy)) {
                    val old = Properties().apply { Files.newInputStream(legacy).use(::load) }
                    check(old == current) { "Both encrypted and divergent legacy settings exist" }
                }
            }
            Files.deleteIfExists(legacy)
            key
        }
    }

    private fun <T> locked(action: () -> T): T = synchronized(processLock) {
        FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            channel.lock().use { action() }
        }
    }

    private fun load(key: ByteArray): Properties {
        val encoded = Files.readAllBytes(file)
        check(encoded.size >= 12 + 16) { "Truncated settings ciphertext" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, encoded, 0, 12))
        cipher.updateAAD(aad)
        val plain = cipher.doFinal(encoded, 12, encoded.size - 12)
        return try {
            Properties().apply { plain.inputStream().use(::load) }
        } finally {
            plain.fill(0)
        }
    }

    private fun save(properties: Properties, key: ByteArray) {
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        val plain = ByteArrayOutputStream().use { properties.store(it, null); it.toByteArray() }
        try {
            atomicWrite(file, nonce + cipher.doFinal(plain))
        } finally {
            plain.fill(0)
        }
    }

    private fun <T> access(action: Settings.() -> T): T = locked {
        val settings = PropertiesSettings(load(key)) { save(it, key) }
        settings.action()
    }

    fun putIfAbsent(key: String, value: String): String = access {
        getStringOrNull(key) ?: value.also { putString(key, it) }
    }

    override val keys: Set<String> get() = access { keys.toSet() }
    override val size: Int get() = access { size }
    override fun clear() = access { clear() }
    override fun remove(key: String) = access { remove(key) }
    override fun hasKey(key: String) = access { hasKey(key) }
    override fun putInt(key: String, value: Int) = access { putInt(key, value) }
    override fun getInt(key: String, defaultValue: Int): Int = access { getInt(key, defaultValue) }
    override fun getIntOrNull(key: String): Int? = access { getIntOrNull(key) }
    override fun putLong(key: String, value: Long) = access { putLong(key, value) }
    override fun getLong(key: String, defaultValue: Long): Long = access { getLong(key, defaultValue) }
    override fun getLongOrNull(key: String): Long? = access { getLongOrNull(key) }
    override fun putFloat(key: String, value: Float) = access { putFloat(key, value) }
    override fun getFloat(key: String, defaultValue: Float): Float = access { getFloat(key, defaultValue) }
    override fun getFloatOrNull(key: String): Float? = access { getFloatOrNull(key) }
    override fun putDouble(key: String, value: Double) = access { putDouble(key, value) }
    override fun getDouble(key: String, defaultValue: Double): Double = access { getDouble(key, defaultValue) }
    override fun getDoubleOrNull(key: String): Double? = access { getDoubleOrNull(key) }
    override fun putBoolean(key: String, value: Boolean) = access { putBoolean(key, value) }
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = access { getBoolean(key, defaultValue) }
    override fun getBooleanOrNull(key: String): Boolean? = access { getBooleanOrNull(key) }
    override fun putString(key: String, value: String) = access { putString(key, value) }
    override fun getString(key: String, defaultValue: String): String = access { getString(key, defaultValue) }
    override fun getStringOrNull(key: String): String? = access { getStringOrNull(key) }

    private companion object {
        val processLock = Any()
    }
}
