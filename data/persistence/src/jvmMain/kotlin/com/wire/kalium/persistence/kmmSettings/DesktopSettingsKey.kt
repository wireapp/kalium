/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.kmmSettings

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.WinCrypt
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.wire.kalium.persistence.atomicWrite
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.HexFormat

/** Caller serializes key creation and file replacement under the settings file lock. */
internal class DesktopSettingsKey(private val file: Path) {
    private val identity = MessageDigest.getInstance("SHA-256").digest(file.toFile().canonicalPath.encodeToByteArray())

    fun load(allowCreation: Boolean): ByteArray = when {
        Platform.isMac() -> macKey(allowCreation)
        Platform.isWindows() -> windowsKey(allowCreation)
        else -> error("Desktop key protection requires macOS or Windows")
    }

    private fun macKey(allowCreation: Boolean): ByteArray {
        val account = HexFormat.of().formatHex(identity)
        val profile = System.getProperty("wire.desktop.dataDir")
        if (!java.lang.Boolean.getBoolean("wire.desktop.stagingFileKeys") ||
            !java.lang.Boolean.getBoolean("wire.desktop.avsLiveTest") || profile.isNullOrBlank()
        ) return MacSettingsKey.load(account, allowCreation)

        val root = Path.of(profile).toRealPath()
        check(file.toFile().canonicalFile.toPath().startsWith(root))
        val directory = root.resolve(".staging-settings-keys")
        val permissions = java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")
        Files.createDirectories(directory, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(permissions))
        check(!Files.isSymbolicLink(directory) && Files.getPosixFilePermissions(directory) == permissions)
        val cached = directory.resolve(account)
        check(!Files.isSymbolicLink(cached))
        return try {
            Files.readAllBytes(cached).also { check(it.size == 32) }
        } catch (missing: NoSuchFileException) {
            // Keychain remains the source of truth when returning to the normal launcher.
            MacSettingsKey.load(account, allowCreation).also { atomicWrite(cached, it) }
        }
    }

    private fun windowsKey(allowCreation: Boolean): ByteArray {
        val keyPath = file.resolveSibling("${file.fileName}.dpapi")
        val flags = WinCrypt.CRYPTPROTECT_UI_FORBIDDEN
        val protected = try {
            Files.readAllBytes(keyPath)
        } catch (missing: NoSuchFileException) {
            check(allowCreation) { "Missing Windows settings key" }
            val key = ByteArray(32).also(SecureRandom()::nextBytes)
            try {
                Crypt32Util.cryptProtectData(key, identity, flags, "", null).also { atomicWrite(keyPath, it) }
            } finally {
                key.fill(0)
            }
        }
        return Crypt32Util.cryptUnprotectData(protected, identity, flags, null).also {
            check(it.size == 32) { "Invalid Windows settings key" }
        }
    }
}

private interface KeychainServices : Library {
    fun SecKeychainFindGenericPassword(
        keychain: Pointer?, serviceLength: Int, service: ByteArray, accountLength: Int, account: ByteArray,
        passwordLength: IntByReference, password: PointerByReference, item: Pointer?
    ): Int
    fun SecKeychainAddGenericPassword(
        keychain: Pointer?, serviceLength: Int, service: ByteArray, accountLength: Int, account: ByteArray,
        passwordLength: Int, password: ByteArray, item: Pointer?
    ): Int
    fun SecKeychainItemFreeContent(attributes: Pointer?, password: Pointer): Int
}

private object MacSettingsKey {
    private val security = Native.load("Security", KeychainServices::class.java)
    private val service = "com.wire.prism.desktop.settings.v1".encodeToByteArray()
    private const val ITEM_NOT_FOUND = -25300
    private const val DUPLICATE_ITEM = -25299

    fun load(identity: String, allowCreation: Boolean): ByteArray {
        val account = identity.encodeToByteArray()
        val length = IntByReference()
        val result = PointerByReference()
        val status = security.SecKeychainFindGenericPassword(null, service.size, service, account.size, account, length, result, null)
        if (status == ITEM_NOT_FOUND) {
            check(allowCreation) { "Missing macOS settings key" }
            val key = ByteArray(32).also(SecureRandom()::nextBytes)
            try {
                val add = security.SecKeychainAddGenericPassword(null, service.size, service, account.size, account, key.size, key, null)
                check(add == 0 || add == DUPLICATE_ITEM) { "Keychain add failed (OSStatus $add)" }
            } finally {
                key.fill(0)
            }
            return load(identity, false)
        }
        check(status == 0) { "Keychain read failed (OSStatus $status)" }
        return try {
            check(length.value == 32) { "Invalid macOS settings key" }
            result.value.getByteArray(0, length.value)
        } finally {
            security.SecKeychainItemFreeContent(null, result.value)
        }
    }
}
