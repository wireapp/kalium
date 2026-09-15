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
import com.sun.jna.WString
import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Win32Exception
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.wire.kalium.persistence.kaliumLogger
import java.util.Base64

/**
 * Protects the settings master key with DPAPI-NG for the current Windows user.
 *
 * The key is protected to the user's SID or to the local user. The SID goes through the Key Distribution Service of the
 * domain controllers, so in an Active Directory domain the user can unprotect the key on every computer of the domain,
 * with a new or a non-persistent profile too. Without a domain, or where the forest has no KDS root key, protecting to
 * the SID fails, and the key is protected to the local user only, which ties it to the profile like classic DPAPI. Once
 * the SID becomes available, for example on the first start with the domain controller in reach, [upgrade] protects
 * such a key to the SID as well. The tests run outside a domain, so they only cover the local user.
 *
 * Unprotecting needs no such choice: the protected key names its protectors, and Windows uses one that works. There is
 * no key store entry: the protected key itself is the reference kept in the key file.
 */
internal class WindowsDpapiNgMasterKeyStore : MasterKeyStore {

    override val name: String = "windows-dpapi-ng"

    override fun store(key: ByteArray): String {
        val protectedKey = try {
            protect(sidOrLocalUser(), key)
        } catch (exception: SettingsEncryptionException) {
            kaliumLogger.i("Protecting the settings master key to the local Windows user only: ${exception.message}")
            protect(LOCAL_USER, key)
        }
        return Base64.getEncoder().encodeToString(protectedKey)
    }

    override fun load(reference: String): ByteArray {
        val protectedKey = decode(reference)
        val key = PointerByReference()
        val size = IntByReference()
        ncrypt.NCryptUnprotectSecret(null, NCRYPT_SILENT_FLAG, protectedKey, protectedKey.size, null, null, key, size)
            .requireSuccess("unprotect the settings master key, for example with a lost profile outside a domain")
        return key.value.readAndFree(size.value)
    }

    /** Protects a key that is protected to the local user only to the SID as well, once the SID is available. */
    @Suppress("SwallowedException")
    override fun upgrade(reference: String, key: ByteArray): String? {
        if (descriptorOf(reference).contains("SID=", ignoreCase = true)) return null
        return try {
            val protectedKey = protect(sidOrLocalUser(), key)
            kaliumLogger.i("Protected the settings master key to the user's SID as well")
            Base64.getEncoder().encodeToString(protectedKey)
        } catch (exception: SettingsEncryptionException) {
            null
        }
    }

    /** The protection descriptor [reference] was protected to, for example `SID=S-1-5-21-… OR LOCAL=user`. */
    internal fun descriptorOf(reference: String): String {
        val protectedKey = decode(reference)
        val descriptor = PointerByReference()
        val key = PointerByReference()
        val size = IntByReference()
        ncrypt.NCryptUnprotectSecret(descriptor, NCRYPT_SILENT_FLAG, protectedKey, protectedKey.size, null, null, key, size)
            .requireSuccess("read how the settings master key is protected")
        key.value.readAndFree(size.value).fill(0)
        try {
            val info = PointerByReference()
            ncrypt.NCryptGetProtectionDescriptorInfo(descriptor.value, null, NCRYPT_PROTECTION_INFO_TYPE_DESCRIPTOR_STRING, info)
                .requireSuccess("read the protection descriptor of the settings master key")
            return info.value.readWideStringAndFree()
        } finally {
            ncrypt.NCryptCloseProtectionDescriptor(descriptor.value)
        }
    }

    private fun sidOrLocalUser() = "SID=${currentUserSid()} OR $LOCAL_USER"

    private fun decode(reference: String): ByteArray = try {
        Base64.getDecoder().decode(reference)
    } catch (exception: IllegalArgumentException) {
        throw SettingsEncryptionException("The settings master key file is damaged", exception)
    }

    private fun protect(descriptor: String, key: ByteArray): ByteArray {
        val handle = PointerByReference()
        ncrypt.NCryptCreateProtectionDescriptor(WString(descriptor), 0, handle)
            .requireSuccess("create the protection descriptor $descriptor")
        try {
            val protectedKey = PointerByReference()
            val size = IntByReference()
            ncrypt.NCryptProtectSecret(handle.value, NCRYPT_SILENT_FLAG, key, key.size, null, null, protectedKey, size)
                .requireSuccess("protect the settings master key to $descriptor")
            return protectedKey.value.readAndFree(size.value)
        } finally {
            ncrypt.NCryptCloseProtectionDescriptor(handle.value)
        }
    }

    private companion object {
        const val LOCAL_USER = "LOCAL=user"
        const val NCRYPT_SILENT_FLAG = 0x40
        const val NCRYPT_PROTECTION_INFO_TYPE_DESCRIPTOR_STRING = 1
    }
}

/** The SID of the account this process runs as. */
private fun currentUserSid(): String {
    val token = WinNT.HANDLEByReference()
    if (!Advapi32.INSTANCE.OpenProcessToken(Kernel32.INSTANCE.GetCurrentProcess(), WinNT.TOKEN_QUERY, token)) {
        throw SettingsEncryptionException("Can't open the access token of this process: ${Kernel32.INSTANCE.GetLastError()}")
    }
    return try {
        Advapi32Util.getTokenAccount(token.value).sidString
    } catch (exception: Win32Exception) {
        throw SettingsEncryptionException("Can't read the account of this process: ${exception.message}", exception)
    } finally {
        Kernel32.INSTANCE.CloseHandle(token.value)
    }
}

private fun Int.requireSuccess(action: String) {
    if (this != ERROR_SUCCESS) throw SettingsEncryptionException("Can't $action: 0x%08X".format(this))
}

/** Copies a buffer that DPAPI-NG allocated, then wipes and frees it. */
private fun Pointer.readAndFree(size: Int): ByteArray = try {
    getByteArray(0, size)
} finally {
    clear(size.toLong())
    Kernel32.INSTANCE.LocalFree(this)
}

/** Reads a string that DPAPI-NG allocated, then frees it. */
private fun Pointer.readWideStringAndFree(): String = try {
    getWideString(0)
} finally {
    Kernel32.INSTANCE.LocalFree(this)
}

private const val ERROR_SUCCESS = 0

@Suppress("FunctionNaming", "LongParameterList")
internal interface NCryptProtect : Library {
    fun NCryptCreateProtectionDescriptor(descriptor: WString, flags: Int, handle: PointerByReference): Int

    fun NCryptCloseProtectionDescriptor(handle: Pointer): Int

    fun NCryptGetProtectionDescriptorInfo(descriptor: Pointer, memoryParameters: Pointer?, infoType: Int, info: PointerByReference): Int

    fun NCryptProtectSecret(
        descriptor: Pointer,
        flags: Int,
        data: ByteArray,
        dataSize: Int,
        memoryParameters: Pointer?,
        window: Pointer?,
        protectedData: PointerByReference,
        protectedDataSize: IntByReference
    ): Int

    fun NCryptUnprotectSecret(
        descriptor: PointerByReference?,
        flags: Int,
        protectedData: ByteArray,
        protectedDataSize: Int,
        memoryParameters: Pointer?,
        window: Pointer?,
        data: PointerByReference,
        dataSize: IntByReference
    ): Int
}

private val ncrypt: NCryptProtect by lazy { Native.load("ncrypt", NCryptProtect::class.java) }
