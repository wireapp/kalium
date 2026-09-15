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

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.util.UUID

/**
 * Keeps the settings master key as a generic password in the macOS Keychain.
 *
 * Uses the classic `SecKeychain…GenericPassword` calls. They are deprecated but still available, and
 * take far less code through JNA than the `SecItem` API with its CoreFoundation dictionaries.
 *
 * @param keychainPath a specific keychain file instead of the user's default keychains, for tests.
 */
internal class MacKeychainMasterKeyStore(private val keychainPath: String? = null) : MasterKeyStore {

    override val name: String = "macos-keychain"

    override fun store(key: ByteArray): String {
        val account = UUID.randomUUID().toString()
        withKeychain { keychain ->
            val status = security.SecKeychainAddGenericPassword(
                keychain,
                SERVICE.length,
                SERVICE,
                account.length,
                account,
                key.size,
                key,
                null
            )
            if (status != ERR_SEC_SUCCESS) {
                throw SettingsEncryptionException("Can't store the settings master key in the keychain (OSStatus $status)")
            }
        }
        return account
    }

    override fun load(reference: String): ByteArray = withKeychain { keychain ->
        val length = IntByReference()
        val data = PointerByReference()
        val status = security.SecKeychainFindGenericPassword(
            keychain,
            SERVICE.length,
            SERVICE,
            reference.length,
            reference,
            length,
            data,
            null
        )
        when (status) {
            ERR_SEC_SUCCESS -> try {
                data.value.getByteArray(0, length.value)
            } finally {
                security.SecKeychainItemFreeContent(null, data.value)
            }

            ERR_SEC_ITEM_NOT_FOUND -> throw SettingsEncryptionException("The settings master key is missing from the keychain")
            else -> throw SettingsEncryptionException("Can't read the settings master key from the keychain (OSStatus $status)")
        }
    }

    private fun <T> withKeychain(block: (Pointer?) -> T): T {
        if (keychainPath == null) return block(null)

        val keychain = PointerByReference()
        val status = security.SecKeychainOpen(keychainPath, keychain)
        if (status != ERR_SEC_SUCCESS) {
            throw SettingsEncryptionException("Can't open the keychain $keychainPath (OSStatus $status)")
        }
        try {
            return block(keychain.value)
        } finally {
            coreFoundation.CFRelease(keychain.value)
        }
    }

    private companion object {
        const val SERVICE = "com.wire.kalium.settings"
        const val ERR_SEC_SUCCESS = 0
        const val ERR_SEC_ITEM_NOT_FOUND = -25300
    }
}

@Suppress("FunctionNaming", "LongParameterList")
internal interface SecurityFramework : Library {
    fun SecKeychainOpen(pathName: String, keychain: PointerByReference): Int

    fun SecKeychainAddGenericPassword(
        keychain: Pointer?,
        serviceNameLength: Int,
        serviceName: String,
        accountNameLength: Int,
        accountName: String,
        passwordLength: Int,
        passwordData: ByteArray,
        itemRef: PointerByReference?
    ): Int

    fun SecKeychainFindGenericPassword(
        keychainOrArray: Pointer?,
        serviceNameLength: Int,
        serviceName: String,
        accountNameLength: Int,
        accountName: String,
        passwordLength: IntByReference,
        passwordData: PointerByReference,
        itemRef: PointerByReference?
    ): Int

    fun SecKeychainItemFreeContent(attrList: Pointer?, data: Pointer?): Int
}

@Suppress("FunctionNaming")
internal interface CoreFoundationFramework : Library {
    fun CFRelease(cf: Pointer?)
}

private val security: SecurityFramework by lazy {
    Native.load("Security", SecurityFramework::class.java)
}

private val coreFoundation: CoreFoundationFramework by lazy {
    Native.load("CoreFoundation", CoreFoundationFramework::class.java)
}
