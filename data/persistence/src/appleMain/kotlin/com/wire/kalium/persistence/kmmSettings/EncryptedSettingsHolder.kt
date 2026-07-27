/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.Settings
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.kCFAllocatorDefault
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.kSecAttrAccessGroup
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner

@OptIn(
    ExperimentalSettingsImplementation::class,
    ExperimentalSettingsApi::class,
    ExperimentalForeignApi::class
)
internal actual fun buildSettings(
    options: SettingOptions,
    param: EncryptedSettingsPlatformParam
): Settings = with(param.keychainConfig) {
    if (accessGroup != null || accessibleAfterFirstUnlock) {
        if (accessibleAfterFirstUnlock) {
            migrateAccessibilityAfterFirstUnlock()
        }
        managedKeychainSettings(
            serviceName = serviceName,
            accessGroup = accessGroup,
            accessibleAfterFirstUnlock = accessibleAfterFirstUnlock
        )
    } else {
        KeychainSettings(serviceName)
    }
}

/**
 * Moves existing entries in this exact service/access-group namespace to the extension-readable
 * accessibility class before [KeychainSettings] starts including that class in every query.
 */
private fun ApplePersistenceConfig.migrateAccessibilityAfterFirstUnlock() {
    val retainedService = CFBridgingRetain(serviceName)
    val retainedAccessGroup = accessGroup?.let { CFBridgingRetain(it) }
    try {
        memScoped {
            val queryProperties = buildMap<CFStringRef?, CFTypeRef?> {
                put(kSecClass, kSecClassGenericPassword)
                put(kSecAttrService, retainedService)
                retainedAccessGroup?.let { put(kSecAttrAccessGroup, it) }
            }
            val query = cfDictionaryOf(queryProperties)
            val update = cfDictionaryOf(
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            )
            val status = SecItemUpdate(query, update)
            CFBridgingRelease(query)
            CFBridgingRelease(update)

            if (status != 0 && status != errSecItemNotFound) {
                error("Unable to migrate Kalium Keychain accessibility (status=$status)")
            }
        }
    } finally {
        CFBridgingRelease(retainedService)
        retainedAccessGroup?.let { CFBridgingRelease(it) }
    }
}

@OptIn(ExperimentalNativeApi::class)
@Suppress("SpreadOperator", "TooGenericExceptionCaught")
private fun managedKeychainSettings(
    serviceName: String,
    accessGroup: String?,
    accessibleAfterFirstUnlock: Boolean
): Settings {
    val retainedService = CFBridgingRetain(serviceName)
    val retainedAccessGroup = accessGroup?.let { CFBridgingRetain(it) }
    return try {
        val properties = buildList {
            add(kSecAttrService to retainedService)
            retainedAccessGroup?.let {
                add(kSecAttrAccessGroup to it)
            }
            if (accessibleAfterFirstUnlock) {
                add(kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
            }
        }
        ManagedKeychainSettings(
            retainedService = retainedService,
            retainedAccessGroup = retainedAccessGroup,
            delegate = KeychainSettings(*properties.toTypedArray())
        )
    } catch (throwable: Throwable) {
        CFBridgingRelease(retainedService)
        retainedAccessGroup?.let { CFBridgingRelease(it) }
        throw throwable
    }
}

@OptIn(ExperimentalNativeApi::class)
private class ManagedKeychainSettings(
    retainedService: CFTypeRef?,
    retainedAccessGroup: CFTypeRef?,
    delegate: KeychainSettings
) : Settings by delegate {
    @Suppress("unused")
    private val serviceCleaner = createCleaner(retainedService) {
        CFBridgingRelease(it)
    }

    @Suppress("unused")
    private val accessGroupCleaner = retainedAccessGroup?.let {
        createCleaner(it) { reference ->
            CFBridgingRelease(reference)
        }
    }
}

@Suppress("SpreadOperator")
private fun MemScope.cfDictionaryOf(
    vararg items: Pair<CFStringRef?, CFTypeRef?>
): CFDictionaryRef? = cfDictionaryOf(mapOf(*items))

@Suppress("SpreadOperator")
private fun MemScope.cfDictionaryOf(
    map: Map<CFStringRef?, CFTypeRef?>
): CFDictionaryRef? {
    val keys = allocArrayOf(*map.keys.toTypedArray())
    val values = allocArrayOf(*map.values.toTypedArray())
    return CFDictionaryCreate(
        kCFAllocatorDefault,
        keys.reinterpret(),
        values.reinterpret(),
        map.size.convert(),
        null,
        null
    )
}

internal actual class EncryptedSettingsPlatformParam(val keychainConfig: ApplePersistenceConfig)
