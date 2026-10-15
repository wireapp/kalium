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
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly
import platform.Security.kSecAttrAccessibleWhenUnlocked
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrService
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner

@OptIn(ExperimentalSettingsImplementation::class)
internal actual fun buildSettings(
    options: SettingOptions,
    param: EncryptedSettingsPlatformParam
): Settings {
    val config = param.keychainConfig
    val accessibility = config.keychainAccessibility ?: return KeychainSettings(config.serviceName)
    return AccessibleKeychainSettings(config.serviceName, accessibility)
}

internal actual class EncryptedSettingsPlatformParam(val keychainConfig: ApplePersistenceConfig)

/**
 * [KeychainSettings] for [service] whose entries are stored and looked up with [accessibility].
 *
 * `KeychainSettings` only takes extra attributes with the service already bridged to Core Foundation,
 * and then doesn't release it, so this releases it once collected, as `KeychainSettings(service)` does.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalSettingsApi::class, ExperimentalSettingsImplementation::class)
private class AccessibleKeychainSettings private constructor(
    cfService: CFTypeRef?,
    accessibility: KeychainAccessibility
) : Settings by KeychainSettings(kSecAttrService to cfService, kSecAttrAccessible to accessibility.secAttrValue) {

    constructor(service: String, accessibility: KeychainAccessibility) : this(CFBridgingRetain(service), accessibility)

    @OptIn(ExperimentalNativeApi::class)
    @Suppress("unused")
    private val cleaner = createCleaner(cfService) { CFBridgingRelease(it) }
}

@OptIn(ExperimentalForeignApi::class)
private val KeychainAccessibility.secAttrValue: CFStringRef?
    get() = when (this) {
        KeychainAccessibility.WhenUnlocked -> kSecAttrAccessibleWhenUnlocked
        KeychainAccessibility.WhenUnlockedThisDeviceOnly -> kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        KeychainAccessibility.AfterFirstUnlock -> kSecAttrAccessibleAfterFirstUnlock
        KeychainAccessibility.AfterFirstUnlockThisDeviceOnly -> kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        KeychainAccessibility.WhenPasscodeSetThisDeviceOnly -> kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly
    }
