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

/**
 * Apple-platform configuration for Kalium's encrypted key/value storage.
 *
 * Consumers (host apps) construct this and hand it to `CoreLogic`. It is then carried
 * through to every Apple-side site that talks to the iOS Keychain.
 *
 * Extend this class — do NOT add new top-level parameters to `CoreLogic` — when new
 * iOS Keychain options (access group, accessibility class, synchronizable, etc.) need
 * to be exposed.
 *
 * @property serviceName Stable identifier used as the iOS Keychain `kSecAttrService`
 *  value for all Kalium-managed entries (auth tokens, passphrases, per-user config).
 *  Must be stable across app reinstalls — typically the host app's bundle identifier.
 *  Must NOT be derived from `NSHomeDirectory()`, which embeds the iOS Application UUID
 *  and changes on every reinstall, orphaning every previously stored entry.
 * @property keychainAccessibility When Kalium-managed entries can be read, as the iOS Keychain
 *  `kSecAttrAccessible` value. Null keeps Apple's default, [KeychainAccessibility.WhenUnlocked],
 *  under which Kalium can't read its tokens and database keys while the device is locked, e.g.
 *  when the app is launched in the background by a push. Apps that run Kalium in the background
 *  should use [KeychainAccessibility.AfterFirstUnlockThisDeviceOnly].
 *  Must be stable across app versions, like [serviceName]: it is part of every Keychain lookup,
 *  so entries stored with a different value are neither found nor overwritten (storing one
 *  again fails), and they are not migrated.
 *  Applies on iOS only; the file-based macOS keychain ignores it.
 */
public data class ApplePersistenceConfig(
    val serviceName: String,
    val keychainAccessibility: KeychainAccessibility?,
)

/**
 * The iOS Keychain `kSecAttrAccessible` values Kalium-managed entries can be stored with.
 * The `ThisDeviceOnly` variants are not restored from a backup onto another device.
 */
public enum class KeychainAccessibility {
    /** `kSecAttrAccessibleWhenUnlocked`, Apple's default. */
    WhenUnlocked,

    /** `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`. */
    WhenUnlockedThisDeviceOnly,

    /** `kSecAttrAccessibleAfterFirstUnlock`: readable while locked, once unlocked after a restart. */
    AfterFirstUnlock,

    /** `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`: readable while locked, once unlocked after a restart. */
    AfterFirstUnlockThisDeviceOnly,

    /** `kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly`: removed when the device passcode is removed. */
    WhenPasscodeSetThisDeviceOnly,
}
