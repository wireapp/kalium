/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.protection

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.nio.file.Path
import java.security.SecureRandom

internal class MacStorageKeyStore(private val keychainFile: Path? = null) : JvmStorageKeyStore {
    override fun load(keyId: String, allowCreation: Boolean): ByteArray {
        require(keyId.isNotBlank() && '\u0000' !in keyId) { "Invalid storage key ID" }
        val account = keyId.encodeToByteArray()
        val keychain = openKeychain()
        try {
            read(keychain, account)?.let { return it }
            check(allowCreation) { "Missing macOS storage key" }

            val candidate = ByteArray(KEY_BYTES).also(random::nextBytes)
            try {
                val status = security.SecKeychainAddGenericPassword(
                    keychain, service.size, service, account.size, account, candidate.size, candidate, null
                )
                check(status == SUCCESS || status == DUPLICATE_ITEM) { "Keychain add failed (OSStatus $status)" }
            } finally {
                candidate.fill(0)
            }

            // Another process may have won the insert. Always use the key actually in Keychain.
            return checkNotNull(read(keychain, account)) { "Storage key disappeared after creation" }
        } finally {
            coreFoundation.CFRelease(keychain)
        }
    }

    private fun openKeychain(): Pointer {
        val result = PointerByReference()
        val status = if (keychainFile == null) {
            security.SecKeychainCopyDefault(result)
        } else {
            security.SecKeychainOpen(keychainFile.toString(), result)
        }
        check(status == SUCCESS) { "Keychain open failed (OSStatus $status)" }
        return checkNotNull(result.value) { "Keychain returned no handle" }
    }

    private fun read(keychain: Pointer, account: ByteArray): ByteArray? {
        val length = IntByReference()
        val data = PointerByReference()
        val status = security.SecKeychainFindGenericPassword(
            keychain, service.size, service, account.size, account, length, data, null
        )
        if (status == ITEM_NOT_FOUND) return null
        check(status == SUCCESS) { "Keychain read failed (OSStatus $status)" }

        val pointer = checkNotNull(data.value) { "Keychain returned no key data" }
        try {
            check(length.value == KEY_BYTES) { "Invalid macOS storage key length" }
            return pointer.getByteArray(0, KEY_BYTES)
        } finally {
            if (length.value == KEY_BYTES) pointer.clear(KEY_BYTES.toLong())
            security.SecKeychainItemFreeContent(null, pointer)
        }
    }

    private companion object {
        const val SUCCESS = 0
        const val ITEM_NOT_FOUND = -25300
        const val DUPLICATE_ITEM = -25299
        const val KEY_BYTES = 32

        val service = "com.wire.kalium.jvm.storage.v1".encodeToByteArray()
        val random = SecureRandom()
        val security = Native.load("Security", KeychainServices::class.java)
        val coreFoundation = Native.load("CoreFoundation", CoreFoundation::class.java)
    }

    private interface KeychainServices : Library {
        fun SecKeychainCopyDefault(result: PointerByReference): Int
        fun SecKeychainOpen(path: String, result: PointerByReference): Int
        fun SecKeychainFindGenericPassword(
            keychain: Pointer,
            serviceLength: Int,
            service: ByteArray,
            accountLength: Int,
            account: ByteArray,
            passwordLength: IntByReference,
            password: PointerByReference,
            item: Pointer?,
        ): Int

        fun SecKeychainAddGenericPassword(
            keychain: Pointer,
            serviceLength: Int,
            service: ByteArray,
            accountLength: Int,
            account: ByteArray,
            passwordLength: Int,
            password: ByteArray,
            item: Pointer?,
        ): Int

        fun SecKeychainItemFreeContent(attributes: Pointer?, password: Pointer): Int
    }

    private interface CoreFoundation : Library {
        fun CFRelease(value: Pointer)
    }
}
