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

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.Settings
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Security.kSecAttrAccessGroup
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccessibleWhenUnlocked
import platform.Security.kSecAttrService
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.Cleaner
import kotlin.native.ref.createCleaner

@OptIn(ExperimentalForeignApi::class, ExperimentalSettingsApi::class, ExperimentalSettingsImplementation::class)
internal actual fun buildSettings(
    options: SettingOptions,
    param: EncryptedSettingsPlatformParam
): Settings {
    val config = param.keychainConfig
    return RetainedKeychainSettings(
        serviceName = config.serviceName,
        accessGroup = config.accessGroup,
        accessibility = config.accessibility,
    )
}

internal actual class EncryptedSettingsPlatformParam(val keychainConfig: ApplePersistenceConfig)

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
private class RetainedKeychainSettings private constructor(
    @Suppress("unused") private val attributes: RetainedKeychainAttributes,
) : Settings by attributes.settings {
    constructor(
        serviceName: String,
        accessGroup: String?,
        accessibility: AppleKeychainAccessibility,
    ) : this(RetainedKeychainAttributes(serviceName, accessGroup, accessibility))
}

@OptIn(
    ExperimentalForeignApi::class,
    ExperimentalNativeApi::class,
    ExperimentalSettingsApi::class,
    ExperimentalSettingsImplementation::class,
)
@Suppress("SpreadOperator") // KeychainSettings exposes custom attributes only through a vararg constructor.
private class RetainedKeychainAttributes(
    serviceName: String,
    accessGroup: String?,
    accessibility: AppleKeychainAccessibility,
) {
    private val retainedService = CFBridgingRetain(serviceName)
    private val retainedAccessGroup = accessGroup?.let { CFBridgingRetain(it) }

    @Suppress("unused")
    private val serviceCleaner: Cleaner = createCleaner(retainedService) { CFBridgingRelease(it) }

    @Suppress("unused")
    private val accessGroupCleaner: Cleaner? = retainedAccessGroup?.let { retainedValue ->
        createCleaner(retainedValue) { CFBridgingRelease(it) }
    }

    val settings: Settings = KeychainSettings(
        *buildList<Pair<CFStringRef?, CFTypeRef?>> {
            add(kSecAttrService to retainedService)
            retainedAccessGroup?.let { add(kSecAttrAccessGroup to it) }
            add(kSecAttrAccessible to accessibility.securityValue())
        }.toTypedArray()
    )
}

@OptIn(ExperimentalForeignApi::class)
internal fun AppleKeychainAccessibility.securityValue(): CFTypeRef? = when (this) {
    AppleKeychainAccessibility.WHEN_UNLOCKED -> kSecAttrAccessibleWhenUnlocked
    AppleKeychainAccessibility.AFTER_FIRST_UNLOCK -> kSecAttrAccessibleAfterFirstUnlock
    AppleKeychainAccessibility.AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY -> kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
}
