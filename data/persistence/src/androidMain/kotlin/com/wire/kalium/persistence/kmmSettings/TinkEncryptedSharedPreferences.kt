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

import android.content.SharedPreferences
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.DeterministicAead
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.util.Collections
import java.util.WeakHashMap

/**
 * [SharedPreferences] whose keys and values are encrypted with Tink before reaching [delegate].
 *
 * - Keys are encrypted deterministically (AES-SIV) so they can still be looked up, with the file name as associated data.
 * - Values are encrypted with AES-GCM, with the plaintext key as associated data, so a ciphertext cannot be moved to
 *   another key.
 * - Each value carries a type tag, so typed getters behave like the platform implementation
 *   (a mismatching type throws [ClassCastException]).
 *
 * Decryption failures surface as [SecurityException], matching `EncryptedSharedPreferences`.
 */
internal class TinkEncryptedSharedPreferences(
    private val fileName: String,
    private val delegate: SharedPreferences,
    private val valueAead: Aead,
    private val keyDeterministicAead: DeterministicAead,
) : SharedPreferences {

    private val keyAssociatedData = fileName.toByteArray(Charsets.UTF_8)

    private val listeners: MutableSet<SharedPreferences.OnSharedPreferenceChangeListener> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    // Held strongly: the platform only keeps weak references to registered listeners.
    private val delegateListener = SharedPreferences.OnSharedPreferenceChangeListener { _, encryptedKey ->
        dispatchChange(encryptedKey)
    }

    override fun getAll(): Map<String, *> =
        delegate.all.entries.associate { (encryptedKey, encryptedValue) ->
            val key = decryptKey(encryptedKey)
            key to decryptValue(key, encryptedValue)
        }

    override fun getString(key: String, defValue: String?): String? =
        read(key) { it as String? } ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        read(key) { it as Set<String>? } ?: defValues

    override fun getInt(key: String, defValue: Int): Int = read(key) { it as Int? } ?: defValue

    override fun getLong(key: String, defValue: Long): Long = read(key) { it as Long? } ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = read(key) { it as Float? } ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = read(key) { it as Boolean? } ?: defValue

    override fun contains(key: String): Boolean = delegate.contains(encryptKey(key))

    override fun edit(): SharedPreferences.Editor = Editor(delegate.edit())

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(listeners) {
            if (listeners.isEmpty()) delegate.registerOnSharedPreferenceChangeListener(delegateListener)
            listeners.add(listener)
        }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(listeners) {
            listeners.remove(listener)
            if (listeners.isEmpty()) delegate.unregisterOnSharedPreferenceChangeListener(delegateListener)
        }
    }

    private fun dispatchChange(encryptedKey: String?) {
        // A null key means the file was cleared (API 30+); forward it as is.
        val key = if (encryptedKey == null) {
            null
        } else {
            runCatching { decryptKey(encryptedKey) }.getOrNull() ?: return
        }
        val snapshot = synchronized(listeners) { listeners.toList() }
        snapshot.forEach { it.onSharedPreferenceChanged(this, key) }
    }

    private inline fun <T> read(key: String, cast: (Any?) -> T): T? {
        val encryptedValue = delegate.getString(encryptKey(key), null) ?: return null
        return cast(decryptValue(key, encryptedValue))
    }

    private fun encryptKey(key: String): String = crypto {
        val ciphertext = keyDeterministicAead.encryptDeterministically(key.toByteArray(Charsets.UTF_8), keyAssociatedData)
        Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    private fun decryptKey(encryptedKey: String): String = crypto {
        val plaintext = keyDeterministicAead.decryptDeterministically(Base64.decode(encryptedKey, Base64.NO_WRAP), keyAssociatedData)
        String(plaintext, Charsets.UTF_8)
    }

    private fun encryptValue(key: String, value: Any): String = crypto {
        val ciphertext = valueAead.encrypt(ValueCodec.encode(value), key.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    private fun decryptValue(key: String, encryptedValue: Any?): Any = crypto {
        val ciphertext = Base64.decode(encryptedValue as String, Base64.NO_WRAP)
        ValueCodec.decode(valueAead.decrypt(ciphertext, key.toByteArray(Charsets.UTF_8)))
    }

    @Suppress("ThrowsCount")
    private inline fun <T> crypto(block: () -> T): T = try {
        block()
    } catch (e: GeneralSecurityException) {
        throw SecurityException("Could not encrypt or decrypt a preference", e)
    } catch (e: IllegalArgumentException) {
        throw SecurityException("Malformed preference", e)
    } catch (e: ClassCastException) {
        throw SecurityException("Malformed preference", e)
    } catch (e: BufferUnderflowException) {
        throw SecurityException("Malformed preference", e)
    } catch (e: NegativeArraySizeException) {
        throw SecurityException("Malformed preference", e)
    }

    private inner class Editor(private val delegateEditor: SharedPreferences.Editor) : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = put(key, value)
        override fun putStringSet(key: String, values: Set<String>?) = put(key, values?.toSet())
        override fun putInt(key: String, value: Int) = put(key, value)
        override fun putLong(key: String, value: Long) = put(key, value)
        override fun putFloat(key: String, value: Float) = put(key, value)
        override fun putBoolean(key: String, value: Boolean) = put(key, value)

        override fun remove(key: String): SharedPreferences.Editor {
            delegateEditor.remove(encryptKey(key))
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            delegateEditor.clear()
            return this
        }

        override fun commit(): Boolean = delegateEditor.commit()

        override fun apply() = delegateEditor.apply()

        private fun put(key: String, value: Any?): SharedPreferences.Editor {
            // Like the platform implementation, a null value removes the key.
            if (value == null) {
                delegateEditor.remove(encryptKey(key))
            } else {
                delegateEditor.putString(encryptKey(key), encryptValue(key, value))
            }
            return this
        }
    }
}

/**
 * Binary encoding of a preference value: one type byte followed by the payload.
 */
internal object ValueCodec {
    private const val TYPE_STRING: Byte = 0
    private const val TYPE_STRING_SET: Byte = 1
    private const val TYPE_INT: Byte = 2
    private const val TYPE_LONG: Byte = 3
    private const val TYPE_FLOAT: Byte = 4
    private const val TYPE_BOOLEAN: Byte = 5

    fun encode(value: Any): ByteArray = when (value) {
        is String -> byteArrayOf(TYPE_STRING) + value.toByteArray(Charsets.UTF_8)
        is Set<*> -> {
            val items = value.map { (it as String).toByteArray(Charsets.UTF_8) }
            ByteBuffer.allocate(1 + Int.SIZE_BYTES + items.sumOf { Int.SIZE_BYTES + it.size }).apply {
                put(TYPE_STRING_SET)
                putInt(items.size)
                items.forEach { putInt(it.size).put(it) }
            }.array()
        }
        is Int -> ByteBuffer.allocate(1 + Int.SIZE_BYTES).put(TYPE_INT).putInt(value).array()
        is Long -> ByteBuffer.allocate(1 + Long.SIZE_BYTES).put(TYPE_LONG).putLong(value).array()
        is Float -> ByteBuffer.allocate(1 + Float.SIZE_BYTES).put(TYPE_FLOAT).putFloat(value).array()
        is Boolean -> byteArrayOf(TYPE_BOOLEAN, (if (value) 1 else 0).toByte())
        else -> throw IllegalArgumentException("Unsupported preference type: ${value::class.java.name}")
    }

    fun decode(bytes: ByteArray): Any {
        require(bytes.isNotEmpty()) { "Empty preference payload" }
        val buffer = ByteBuffer.wrap(bytes, 1, bytes.size - 1)
        val value: Any = when (bytes[0]) {
            TYPE_STRING -> String(bytes, 1, bytes.size - 1, Charsets.UTF_8)
            TYPE_STRING_SET -> {
                val count = buffer.int
                require(count >= 0) { "Negative set size" }
                HashSet<String>(count).apply {
                    repeat(count) {
                        val item = ByteArray(buffer.int)
                        buffer.get(item)
                        add(String(item, Charsets.UTF_8))
                    }
                }
            }
            TYPE_INT -> buffer.int
            TYPE_LONG -> buffer.long
            TYPE_FLOAT -> buffer.float
            TYPE_BOOLEAN -> buffer.get() != 0.toByte()
            else -> throw IllegalArgumentException("Unknown preference type: ${bytes[0]}")
        }
        require(bytes[0] == TYPE_STRING || !buffer.hasRemaining()) { "Trailing bytes in preference payload" }
        return value
    }
}
