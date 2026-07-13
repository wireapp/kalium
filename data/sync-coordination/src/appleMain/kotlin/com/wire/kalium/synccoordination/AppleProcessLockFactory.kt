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

package com.wire.kalium.synccoordination

/**
 * Apple cross-process synchronization primitive for one account/client scope.
 *
 * [sharedRoot] must be the absolute App Group directory supplied by the host. The implementation
 * creates this versioned cross-language layout:
 *
 * `<sharedRoot>/kalium-nse/v1/<account-client-digest>/sync.lock`
 *
 * The digest is SHA-256 over the UTF-8 bytes of
 * `com.wire.kalium.notification-sync.lock/v1`, followed by a four-byte big-endian account-ID byte
 * length, the account-ID bytes, a four-byte big-endian client-ID byte length, and the client-ID
 * bytes. Raw identifiers never become path components. Native Swift must reproduce this exact
 * derivation; `probe-account` plus `probe-client` yields
 * `9c03173842651044f0848cfb08e7ef905916c4eae2d198cb7ab691d9124ee5ba`.
 *
 * The caller-owned root is required to be a real directory owned by the effective user, but its
 * mode is left to App Group container policy. Every descendant created or reused by this module
 * must have exact `0700` directory permissions; the retained lock file must have exact `0600`.
 *
 * The same derived lock must cover authentication refresh, cursor and inbox access, CoreCrypto,
 * the complete synchronization attempt, and notification processing in both the app and NSE.
 * Lock-file existence is not ownership: ownership is the open descriptor with a successful
 * `flock(LOCK_EX | LOCK_NB)`. The file is deliberately never deleted on release.
 */
public class AppleProcessLockFactory(
    private val sharedRoot: String
) {
    /** Performs one immediate acquisition attempt and never waits or polls for an owner. */
    public fun tryAcquire(accountId: String, clientId: String): ProcessLockAcquireResult = try {
        val accountBytes = validatedIdentityBytes(accountId)
        val clientBytes = validatedIdentityBytes(clientId)
        if (!isValidSharedRoot(sharedRoot)) {
            ProcessLockAcquireResult.TerminalFailure(TerminalProcessLockFailure.INVALID_SHARED_ROOT)
        } else {
            acquireAppleProcessLock(sharedRoot, accountBytes, clientBytes)
        }
    } catch (_: InvalidProcessLockIdentity) {
        ProcessLockAcquireResult.TerminalFailure(TerminalProcessLockFailure.INVALID_LOCK_IDENTITY)
    } catch (_: Throwable) {
        ProcessLockAcquireResult.TerminalFailure(TerminalProcessLockFailure.UNEXPECTED_PLATFORM_FAILURE)
    }
}

internal expect fun acquireAppleProcessLock(
    sharedRoot: String,
    accountId: ByteArray,
    clientId: ByteArray
): ProcessLockAcquireResult

private fun isValidSharedRoot(value: String): Boolean {
    val hasValidPrefix = value.isNotEmpty() && value.startsWith(PATH_SEPARATOR)
    val hasValidLength = value.length > PATH_SEPARATOR.length && value.length <= MAX_SHARED_ROOT_LENGTH
    val hasValidContent = !value.endsWith(PATH_SEPARATOR) && value.indexOf(NULL_CHARACTER) < 0
    val hasValidComponents = value.drop(PATH_SEPARATOR.length).split(PATH_SEPARATOR).all { component ->
        component.isNotEmpty() && component != CURRENT_DIRECTORY && component != PARENT_DIRECTORY
    }
    return hasValidPrefix && hasValidLength && hasValidContent && hasValidComponents
}

private fun validatedIdentityBytes(value: String): ByteArray {
    if (value.isEmpty() || value.indexOf(NULL_CHARACTER) >= 0) {
        throw InvalidProcessLockIdentity()
    }
    return value.encodeToByteArray().also { bytes ->
        if (bytes.size > MAX_IDENTITY_BYTE_LENGTH) throw InvalidProcessLockIdentity()
    }
}

private class InvalidProcessLockIdentity : Exception()

internal const val IDENTITY_DIGEST_PREFIX: String = "com.wire.kalium.notification-sync.lock/v1"
internal const val PRODUCT_DIRECTORY: String = "kalium-nse"
internal const val VERSION_DIRECTORY: String = "v1"
internal const val LOCK_FILE_NAME: String = "sync.lock"
internal const val PRIVATE_DIRECTORY_MODE: Int = 448
internal const val PRIVATE_FILE_MODE: Int = 384
internal const val CLOSED_DESCRIPTOR: Int = -1
internal const val SINGLE_LINK_COUNT: Long = 1L
internal const val FILE_PERMISSION_MASK: Int = 511
internal const val LENGTH_FIELD_SIZE: Int = 4

private const val PATH_SEPARATOR = "/"
private const val CURRENT_DIRECTORY = "."
private const val PARENT_DIRECTORY = ".."
private const val NULL_CHARACTER = '\u0000'
private const val MAX_SHARED_ROOT_LENGTH = 4_096
private const val MAX_IDENTITY_BYTE_LENGTH = 4_096
