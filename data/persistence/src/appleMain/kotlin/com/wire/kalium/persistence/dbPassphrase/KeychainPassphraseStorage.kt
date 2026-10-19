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

package com.wire.kalium.persistence.dbPassphrase

import com.wire.kalium.persistence.kmmSettings.ApplePersistenceConfig
import com.wire.kalium.persistence.kmmSettings.secAttrValue
import kotlinx.cinterop.convert
import kotlinx.cinterop.ptr
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.errSecDuplicateItem
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessGroup
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecValueData

/**
 * [PassphraseStorage] on the Keychain whose [setPassphraseIfAbsent] only ever adds an item: the Keychain rejects a
 * second item for the same key, so a passphrase another process or thread stored in between is never replaced.
 *
 * @param keychainConfig the attributes [delegate] uses for the shared Keychain items.
 */
internal class KeychainPassphraseStorage(
    private val delegate: PassphraseStorage,
    private val keychainConfig: ApplePersistenceConfig,
) : PassphraseStorage by delegate {

    override fun setPassphraseIfAbsent(key: String, passphrase: String): String =
        getPassphrase(key) ?: if (addKeychainItem(key, passphrase)) {
            passphrase
        } else {
            checkNotNull(getPassphrase(key)) { "The Keychain rejected a passphrase as a duplicate but returns none" }
        }

    /**
     * Adds [value] under [key] with the attributes the `KeychainSettings` behind [delegate] stores strings with, so
     * [delegate] reads it back. Returns false if there is an item for [key] already.
     */
    private fun addKeychainItem(key: String, value: String): Boolean {
        val service = CFBridgingRetain(keychainConfig.serviceName)
        val account = CFBridgingRetain(key)
        val accessGroup = keychainConfig.accessGroup?.let { CFBridgingRetain(it) }
        val data = CFBridgingRetain(NSString.create(string = value).dataUsingEncoding(NSUTF8StringEncoding))
        val attributes = CFDictionaryCreateMutable(
            null,
            ATTRIBUTE_COUNT.convert(),
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr
        )
        try {
            CFDictionaryAddValue(attributes, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(attributes, kSecAttrService, service)
            CFDictionaryAddValue(attributes, kSecAttrAccount, account)
            accessGroup?.let { CFDictionaryAddValue(attributes, kSecAttrAccessGroup, it) }
            keychainConfig.keychainAccessibility?.let { CFDictionaryAddValue(attributes, kSecAttrAccessible, it.secAttrValue) }
            CFDictionaryAddValue(attributes, kSecValueData, data)
            return when (val status = SecItemAdd(attributes, null)) {
                errSecSuccess -> true
                errSecDuplicateItem -> false
                else -> error("Keychain error $status")
            }
        } finally {
            CFRelease(attributes)
            CFBridgingRelease(service)
            CFBridgingRelease(account)
            accessGroup?.let { CFBridgingRelease(it) }
            CFBridgingRelease(data)
        }
    }

    private companion object {
        const val ATTRIBUTE_COUNT = 6
    }
}
