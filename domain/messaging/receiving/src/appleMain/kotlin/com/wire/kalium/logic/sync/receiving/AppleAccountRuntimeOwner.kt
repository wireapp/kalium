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
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class,
    com.wire.kalium.util.InternalKaliumApi::class,
)

package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.utils.calcSHA256
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.util.InternalKaliumApi
import com.wire.kalium.util.string.toHexString
import kotlin.concurrent.atomics.AtomicInt
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.posix.EACCES
import platform.posix.EAGAIN
import platform.posix.EEXIST
import platform.posix.ENOENT
import platform.posix.EPERM
import platform.posix.EWOULDBLOCK
import platform.posix.LOCK_EX
import platform.posix.LOCK_NB
import platform.posix.O_CLOEXEC
import platform.posix.O_CREAT
import platform.posix.O_RDWR
import platform.posix.S_IRUSR
import platform.posix.S_IRWXU
import platform.posix.S_IWUSR
import platform.posix.close
import platform.posix.errno
import platform.posix.flock
import platform.posix.mkdir
import platform.posix.open

/**
 * Exclusive account ownership shared by Apple app and extension processes.
 *
 * [rootPath] is the shared Kalium App Group root. Lock files live outside account directories and
 * must never be deleted during logout: all owners must continue locking the same inode.
 * This primitive does not open storage. The runtime must close its resources before [AccountRuntimeLease.release].
 */
@InternalKaliumApi
public class AppleAccountRuntimeOwner internal constructor(
    private val rootPath: String,
    private val onLockAcquired: () -> Unit,
) {
    private val paths = AccountRuntimeLockPaths(rootPath)

    public constructor(rootPath: String) : this(rootPath, {})

    /**
     * For an extension with a limited execution budget: one attempt, or attempts until [retryFor] passes
     * while another owner holds the account. A retrying attempt also waits for another attempt of this process,
     * which holds the account only within its own budget and presents only what it received.
     */
    public suspend fun tryAcquire(
        accountId: UserId,
        retryFor: Duration = Duration.ZERO,
    ): Either<AccountRuntimeFailure, AccountRuntimeLease> = acquire(accountId, retryFor)

    /** Wait cancellably for app ownership. Waiting for one account does not block other accounts. */
    public suspend fun acquire(accountId: UserId): Either<AccountRuntimeFailure, AccountRuntimeLease> =
        acquire(accountId, Duration.INFINITE)

    private suspend fun acquire(accountId: UserId, waitFor: Duration): Either<AccountRuntimeFailure, AccountRuntimeLease> {
        coroutineContext.ensureActive()
        val descriptor = when (val result = openLockFile(accountId)) {
            is Either.Left -> return result
            is Either.Right -> result.value
        }
        var lease: AccountRuntimeLease? = null
        val result = try {
            coroutineContext.ensureActive()
            val lockFile = paths.filePath(accountId)
            val failure = lock(descriptor, lockFile, waitFor)
            if (failure == null) {
                onLockAcquired()
                val acquiredLease = AccountRuntimeLease(accountId, descriptor, rootPath, lockFile)
                lease = acquiredLease
                Either.Right(acquiredLease)
            } else {
                Either.Left(failure)
            }
        } finally {
            if (lease == null) close(descriptor)
        }
        return when (result) {
            is Either.Left -> result
            is Either.Right -> suspendCancellableCoroutine { continuation ->
                continuation.resume(result) { _, _, _ -> result.value.release() }
            }
        }
    }

    private suspend fun lock(descriptor: Int, lockFile: String, waitFor: Duration): AccountRuntimeFailure? {
        var failure = tryLock(descriptor)
        // Elapsed time bounds the window, so slow wake-ups do not stretch it.
        withTimeoutOrNull(waitFor) {
            while (failure == AccountRuntimeFailure.BUSY) failure = retryLock(descriptor)
        }
        // flock refuses this process's other descriptors too. Unlike another owner, an attempt of this process ends.
        while (failure == AccountRuntimeFailure.BUSY && waitFor > Duration.ZERO && lockFile in heldLockFiles.value) {
            failure = retryLock(descriptor)
        }
        return failure
    }

    private suspend fun retryLock(descriptor: Int): AccountRuntimeFailure? {
        delay(LOCK_RETRY_DELAY)
        return tryLock(descriptor)
    }

    private fun tryLock(descriptor: Int): AccountRuntimeFailure? = if (flock(descriptor, LOCK_EX or LOCK_NB) == 0) {
        null
    } else {
        when (val error = errno) {
            EWOULDBLOCK, EAGAIN -> AccountRuntimeFailure.BUSY
            else -> error.toAccountRuntimeFailure()
        }
    }

    internal fun lockFilePath(accountId: UserId): String = paths.filePath(accountId)

    private fun openLockFile(accountId: UserId): Either<AccountRuntimeFailure, Int> {
        if (mkdir(paths.directoryPath, S_IRWXU.toUShort()) != 0 && errno != EEXIST) {
            return Either.Left(errno.toAccountRuntimeFailure())
        }
        val descriptor = open(paths.filePath(accountId), O_CREAT or O_RDWR or O_CLOEXEC, S_IRUSR or S_IWUSR)
        return if (descriptor < 0) Either.Left(errno.toAccountRuntimeFailure()) else Either.Right(descriptor)
    }

    private companion object {
        private val LOCK_RETRY_DELAY = 20.milliseconds
    }
}

@InternalKaliumApi
public enum class AccountRuntimeFailure {
    BUSY,
    NOT_PREPARED,
    INCOMPATIBLE,
    MISSING_STORAGE,
    PROTECTED_STORAGE,
    STORAGE,
}

/** Lock files this process holds a lease for. */
private val heldLockFiles = MutableStateFlow<Set<String>>(emptySet())

/** Hold this lease until account workers stop and every account resource closes successfully. */
@InternalKaliumApi
public class AccountRuntimeLease internal constructor(
    public val accountId: UserId,
    private val descriptor: Int,
    private val rootPath: String,
    private val lockFile: String,
) {
    private val released = AtomicInt(0)

    init {
        heldLockFiles.update { it + lockFile }
    }

    internal fun requireHeld(rootPath: String, accountId: UserId) {
        check(released.load() == 0) { "Account lease has already been released" }
        require(this.rootPath == rootPath && this.accountId == accountId) { "Account lease does not own this account storage" }
    }

    public fun release() {
        if (released.compareAndSet(expectedValue = 0, newValue = 1)) {
            heldLockFiles.update { it - lockFile }
            // close releases flock. Never retry an interrupted close: the fd number may be reused.
            close(descriptor)
        }
    }
}

internal class AccountRuntimeLockPaths(rootPath: String) {
    private val normalizedRootPath = rootPath.trimEnd('/')

    init {
        require(normalizedRootPath.startsWith('/')) {
            "The shared Kalium root path must be absolute"
        }
    }

    val directoryPath: String = "$normalizedRootPath/$LOCK_DIRECTORY_NAME"

    fun filePath(accountId: UserId): String = "$directoryPath/account-${appleAccountFileKey(accountId)}.lock"

    private companion object {
        private const val LOCK_DIRECTORY_NAME = "account-runtime-locks"
    }
}

/**
 * Names [accountId]'s files outside its account directory, such as its lock: the versioned hash of its qualified
 * identity. Changing it would point processes at different lock files.
 */
@InternalKaliumApi
public fun appleAccountFileKey(accountId: UserId): String {
    val canonicalIdentity = buildString {
        append(ACCOUNT_IDENTITY_VERSION)
        append(accountId.value.encodeToByteArray().size)
        append(':')
        append(accountId.value)
        append(accountId.domain.encodeToByteArray().size)
        append(':')
        append(accountId.domain)
    }
    return calcSHA256(canonicalIdentity.encodeToByteArray()).toHexString()
}

private const val ACCOUNT_IDENTITY_VERSION = "kalium-account-runtime-lock-v1\n"

private fun Int.toAccountRuntimeFailure(): AccountRuntimeFailure = when (this) {
    ENOENT -> AccountRuntimeFailure.MISSING_STORAGE
    EACCES, EPERM -> AccountRuntimeFailure.PROTECTED_STORAGE
    else -> AccountRuntimeFailure.STORAGE
}
