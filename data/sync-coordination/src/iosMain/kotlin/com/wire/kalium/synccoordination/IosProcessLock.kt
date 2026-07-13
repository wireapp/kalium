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

@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class
)

package com.wire.kalium.synccoordination

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.posix.EACCES
import platform.posix.EAGAIN
import platform.posix.EBADF
import platform.posix.EBUSY
import platform.posix.EDQUOT
import platform.posix.EEXIST
import platform.posix.EINTR
import platform.posix.EINVAL
import platform.posix.EIO
import platform.posix.EISDIR
import platform.posix.ELOOP
import platform.posix.EMFILE
import platform.posix.ENAMETOOLONG
import platform.posix.ENFILE
import platform.posix.ENOENT
import platform.posix.ENOMEM
import platform.posix.ENOLCK
import platform.posix.ENOSPC
import platform.posix.ENOTDIR
import platform.posix.ENOTSUP
import platform.posix.EOPNOTSUPP
import platform.posix.EPERM
import platform.posix.EROFS
import platform.posix.ESTALE
import platform.posix.EWOULDBLOCK
import platform.posix.LOCK_EX
import platform.posix.LOCK_NB
import platform.posix.LOCK_UN
import platform.posix.O_CLOEXEC
import platform.posix.O_CREAT
import platform.posix.O_DIRECTORY
import platform.posix.O_NOFOLLOW
import platform.posix.O_NOFOLLOW_ANY
import platform.posix.O_RDONLY
import platform.posix.O_RDWR
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.S_IFREG
import platform.posix.S_IWGRP
import platform.posix.S_IWOTH
import platform.posix.close
import platform.posix.errno
import platform.posix.flock
import platform.posix.fstat
import platform.posix.geteuid
import platform.posix.mkdirat
import platform.posix.open
import platform.posix.openat
import platform.posix.stat
import kotlin.concurrent.atomics.AtomicInt

internal actual fun acquireAppleProcessLock(
    sharedRoot: String,
    accountId: ByteArray,
    clientId: ByteArray
): ProcessLockAcquireResult = try {
    IosProcessLockAcquirer(sharedRoot, deriveIdentityDigest(accountId, clientId)).acquire()
} catch (failure: IosLockAcquisitionFailure) {
    failure.result
}

private class IosProcessLockAcquirer(
    private val sharedRoot: String,
    private val lockIdentityDigest: String
) {
    fun acquire(): ProcessLockAcquireResult {
        var rootDescriptor = CLOSED_DESCRIPTOR
        var productDescriptor = CLOSED_DESCRIPTOR
        var versionDescriptor = CLOSED_DESCRIPTOR
        var accountDescriptor = CLOSED_DESCRIPTOR
        var lockDescriptor = CLOSED_DESCRIPTOR
        try {
            rootDescriptor = openRootDirectory()
            productDescriptor = openOrCreatePrivateDirectory(rootDescriptor, PRODUCT_DIRECTORY)
            versionDescriptor = openOrCreatePrivateDirectory(productDescriptor, VERSION_DIRECTORY)
            accountDescriptor = openOrCreatePrivateDirectory(versionDescriptor, lockIdentityDigest)
            lockDescriptor = openLockFile(accountDescriptor)

            if (flock(lockDescriptor, LOCK_EX or LOCK_NB) != 0) {
                val lockError = errno
                if (lockError == EWOULDBLOCK || lockError == EAGAIN) {
                    return ProcessLockAcquireResult.Unavailable
                }
                failForLockError(lockError)
            }

            val acquired = ProcessLockAcquireResult.Acquired(IosProcessLockLease(lockDescriptor))
            lockDescriptor = CLOSED_DESCRIPTOR
            return acquired
        } finally {
            closeOnce(lockDescriptor)
            closeOnce(accountDescriptor)
            closeOnce(versionDescriptor)
            closeOnce(productDescriptor)
            closeOnce(rootDescriptor)
        }
    }

    private fun openRootDirectory(): Int {
        val descriptor = open(sharedRoot, O_RDONLY or O_DIRECTORY or O_NOFOLLOW_ANY or O_CLOEXEC)
        if (descriptor < 0) failForRootError(errno)
        var descriptorTransferred = false
        try {
            validateDirectory(descriptor, requirePrivateMode = false)
            descriptorTransferred = true
            return descriptor
        } finally {
            if (!descriptorTransferred) closeOnce(descriptor)
        }
    }

    private fun openOrCreatePrivateDirectory(parentDescriptor: Int, name: String): Int {
        val mkdirResult = mkdirat(parentDescriptor, name, PRIVATE_DIRECTORY_MODE.toUShort())
        if (mkdirResult != 0) {
            val mkdirError = errno
            if (mkdirError != EEXIST) failForPathError(mkdirError)
        }

        val descriptor = openat(
            parentDescriptor,
            name,
            O_RDONLY or O_DIRECTORY or O_NOFOLLOW or O_CLOEXEC
        )
        if (descriptor < 0) failForPathError(errno)
        var descriptorTransferred = false
        try {
            validateDirectory(descriptor, requirePrivateMode = true)
            descriptorTransferred = true
            return descriptor
        } finally {
            if (!descriptorTransferred) closeOnce(descriptor)
        }
    }

    private fun openLockFile(parentDescriptor: Int): Int {
        val descriptor = openat(
            parentDescriptor,
            LOCK_FILE_NAME,
            O_RDWR or O_CREAT or O_NOFOLLOW or O_CLOEXEC,
            PRIVATE_FILE_MODE
        )
        if (descriptor < 0) failForPathError(errno)
        var descriptorTransferred = false
        try {
            validateLockFile(descriptor)
            descriptorTransferred = true
            return descriptor
        } finally {
            if (!descriptorTransferred) closeOnce(descriptor)
        }
    }
}

private class IosProcessLockLease(descriptor: Int) : ProcessLockLease {
    private val ownedDescriptor = AtomicInt(descriptor)

    override fun release() {
        val descriptor = ownedDescriptor.exchange(CLOSED_DESCRIPTOR)
        if (descriptor < 0) return
        runCatching { flock(descriptor, LOCK_UN) }
        runCatching { close(descriptor) }
    }
}

private fun validateDirectory(descriptor: Int, requirePrivateMode: Boolean): Unit = memScoped {
    val metadata = alloc<stat>()
    if (fstat(descriptor, metadata.ptr) != 0) failForPathError(errno)
    val mode = metadata.st_mode.toInt()
    if ((mode and S_IFMT) != S_IFDIR || metadata.st_uid != geteuid()) {
        failTerminal(TerminalProcessLockFailure.UNSAFE_FILE_SYSTEM_ENTRY)
    }
    if (requirePrivateMode && (mode and FILE_PERMISSION_MASK) != PRIVATE_DIRECTORY_MODE) {
        failTerminal(TerminalProcessLockFailure.UNSAFE_FILE_SYSTEM_ENTRY)
    }
}

private fun validateLockFile(descriptor: Int): Unit = memScoped {
    val metadata = alloc<stat>()
    if (fstat(descriptor, metadata.ptr) != 0) failForPathError(errno)
    val mode = metadata.st_mode.toInt()
    val hasUnsafeIdentity =
        (mode and S_IFMT) != S_IFREG || metadata.st_uid != geteuid()
    val hasUnsafePermissionsOrLinks =
        metadata.st_nlink.toLong() != SINGLE_LINK_COUNT ||
                (mode and FILE_PERMISSION_MASK) != PRIVATE_FILE_MODE ||
                (mode and (S_IWGRP or S_IWOTH)) != 0
    if (hasUnsafeIdentity || hasUnsafePermissionsOrLinks) {
        failTerminal(TerminalProcessLockFailure.UNSAFE_FILE_SYSTEM_ENTRY)
    }
}

private fun deriveIdentityDigest(accountId: ByteArray, clientId: ByteArray): String {
    val prefixBytes = IDENTITY_DIGEST_PREFIX.encodeToByteArray()
    val input = ByteArray(
        prefixBytes.size + LENGTH_FIELD_SIZE + accountId.size + LENGTH_FIELD_SIZE + clientId.size
    )
    var offset = 0
    prefixBytes.copyInto(input, offset)
    offset += prefixBytes.size
    offset = writeLength(input, offset, accountId.size)
    accountId.copyInto(input, offset)
    offset += accountId.size
    offset = writeLength(input, offset, clientId.size)
    clientId.copyInto(input, offset)

    val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
    val pointer = input.usePinned { inputPinned ->
        digest.usePinned { digestPinned ->
            CC_SHA256(
                inputPinned.addressOf(0),
                input.size.toUInt(),
                digestPinned.addressOf(0).reinterpret<UByteVar>()
            )
        }
    }
    if (pointer == null) failRetryable(RetryableProcessLockFailure.IO_FAILURE)
    return digest.toLowerHex()
}

private fun failForRootError(error: Int): Nothing = when (error) {
    ENOENT, ENOTDIR, ELOOP, ENAMETOOLONG, EINVAL ->
        failTerminal(TerminalProcessLockFailure.INVALID_SHARED_ROOT)
    else -> failForPathError(error)
}

private fun failForPathError(error: Int): Nothing = when (error) {
    EACCES, EPERM -> failTerminal(TerminalProcessLockFailure.PERMISSION_DENIED)
    EROFS -> failTerminal(TerminalProcessLockFailure.READ_ONLY_FILE_SYSTEM)
    ELOOP, ENOTDIR, EISDIR, ENOENT, ENAMETOOLONG, EINVAL ->
        failTerminal(TerminalProcessLockFailure.UNSAFE_FILE_SYSTEM_ENTRY)
    ENOSPC, EDQUOT -> failRetryable(RetryableProcessLockFailure.STORAGE_EXHAUSTED)
    EMFILE -> failRetryable(RetryableProcessLockFailure.PROCESS_RESOURCE_EXHAUSTED)
    ENFILE, ENOMEM -> failRetryable(RetryableProcessLockFailure.SYSTEM_RESOURCE_EXHAUSTED)
    EINTR -> failRetryable(RetryableProcessLockFailure.INTERRUPTED)
    EIO, EBUSY, ESTALE -> failRetryable(RetryableProcessLockFailure.IO_FAILURE)
    else -> failTerminal(TerminalProcessLockFailure.UNEXPECTED_PLATFORM_FAILURE)
}

private fun failForLockError(error: Int): Nothing = when (error) {
    EINTR -> failRetryable(RetryableProcessLockFailure.INTERRUPTED)
    EBADF, EINVAL, ENOTSUP, EOPNOTSUPP ->
        failTerminal(TerminalProcessLockFailure.LOCKING_NOT_SUPPORTED)
    EIO, EBUSY, ESTALE, ENOLCK -> failRetryable(RetryableProcessLockFailure.IO_FAILURE)
    else -> failTerminal(TerminalProcessLockFailure.UNEXPECTED_PLATFORM_FAILURE)
}

private fun failRetryable(reason: RetryableProcessLockFailure): Nothing =
    throw IosLockAcquisitionFailure(ProcessLockAcquireResult.RetryableFailure(reason))

private fun failTerminal(reason: TerminalProcessLockFailure): Nothing =
    throw IosLockAcquisitionFailure(ProcessLockAcquireResult.TerminalFailure(reason))

private fun closeOnce(descriptor: Int) {
    if (descriptor >= 0) close(descriptor)
}

private fun writeLength(destination: ByteArray, offset: Int, value: Int): Int {
    destination[offset] = (value ushr MOST_SIGNIFICANT_BYTE_SHIFT).toByte()
    destination[offset + SECOND_BYTE_OFFSET] = (value ushr SECOND_BYTE_SHIFT).toByte()
    destination[offset + THIRD_BYTE_OFFSET] = (value ushr THIRD_BYTE_SHIFT).toByte()
    destination[offset + FOURTH_BYTE_OFFSET] = value.toByte()
    return offset + LENGTH_FIELD_SIZE
}

private fun ByteArray.toLowerHex(): String = buildString(size * HEX_CHARACTERS_PER_BYTE) {
    for (byte in this@toLowerHex) {
        val value = byte.toInt() and UNSIGNED_BYTE_MASK
        append(HEX_ALPHABET[value ushr HEX_NIBBLE_BITS])
        append(HEX_ALPHABET[value and HEX_NIBBLE_MASK])
    }
}

private class IosLockAcquisitionFailure(
    val result: ProcessLockAcquireResult
) : Exception()

private const val HEX_CHARACTERS_PER_BYTE = 2
private const val HEX_NIBBLE_BITS = 4
private const val HEX_NIBBLE_MASK = 0x0F
private const val UNSIGNED_BYTE_MASK = 0xFF
private const val HEX_ALPHABET = "0123456789abcdef"
private const val MOST_SIGNIFICANT_BYTE_SHIFT = 24
private const val SECOND_BYTE_SHIFT = 16
private const val THIRD_BYTE_SHIFT = 8
private const val SECOND_BYTE_OFFSET = 1
private const val THIRD_BYTE_OFFSET = 2
private const val FOURTH_BYTE_OFFSET = 3
