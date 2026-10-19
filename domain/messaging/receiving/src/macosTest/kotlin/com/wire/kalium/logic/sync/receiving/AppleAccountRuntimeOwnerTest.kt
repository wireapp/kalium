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
    kotlinx.coroutines.DelicateCoroutinesApi::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
    com.wire.kalium.util.InternalKaliumApi::class,
)

package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.user.UserId
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.posix.stat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class AppleAccountRuntimeOwnerTest {
    @Test
    fun relativeSharedRootIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            AppleAccountRuntimeOwner("relative/root")
        }
    }

    @Test
    fun lockPathIsStableAndDoesNotExposeAccountIdentity() {
        val paths = AccountRuntimeLockPaths("/shared/app-group/Kalium/")
        val accountId = UserId("user-123", "example.com")

        val path = paths.filePath(accountId)

        assertEquals(
            "/shared/app-group/Kalium/account-runtime-locks/" +
                "account-872e5fa68f09bb4d750e11ef78b9840598c07981032c09e6f8c1ce48f14065dd.lock",
            path,
        )
        val fileName = path.substringAfterLast('/')
        assertFalse(fileName.contains(accountId.value))
        assertFalse(fileName.contains(accountId.domain))
    }

    @Test
    fun missingSharedRootIsUnavailableAndIsNotCreated() = runTest {
        val rootPath = "${NSTemporaryDirectory()}/missing-kalium-root-${NSUUID().UUIDString}/Kalium"
        val owner = AppleAccountRuntimeOwner(rootPath)

        assertEquals(
            Either.Left(AccountRuntimeFailure.MISSING_STORAGE),
            owner.tryAcquire(UserId("user", "example.com")),
        )
        assertFalse(NSFileManager.defaultManager.fileExistsAtPath(rootPath))
    }

    @Test
    fun preCancelledTryAcquireDoesNotLeaveTheAccountLocked() = withLockRoot { rootPath ->
        val accountId = UserId("cancelled-nse-account", "example.com")
        val owner = AppleAccountRuntimeOwner(rootPath)
        val attempt = async(start = CoroutineStart.ATOMIC) {
            owner.tryAcquire(accountId)
        }

        attempt.cancel()
        runCurrent()

        assertTrue(attempt.isCancelled)
        assertFalse(NSFileManager.defaultManager.fileExistsAtPath(owner.lockFilePath(accountId)))
        owner.acquireLease(accountId).release()
    }

    @Test
    fun cancellationAfterTryAcquireLocksClosesTheUndeliveredLease() = withLockRoot { rootPath ->
        val accountId = UserId("cancelled-after-nse-lock", "example.com")
        val cancellation = Job(coroutineContext[Job])
        val acquisitionScope = CoroutineScope(coroutineContext + cancellation)
        var lockWasAcquired = false
        val owner = AppleAccountRuntimeOwner(rootPath) {
            lockWasAcquired = true
            cancellation.cancel()
        }
        val attempt = acquisitionScope.async {
            owner.tryAcquire(accountId)
        }

        runCurrent()
        attempt.join()

        assertTrue(lockWasAcquired)
        assertTrue(attempt.isCancelled)
        AppleAccountRuntimeOwner(rootPath).acquireLease(accountId).release()
    }

    @Test
    fun cancellationAfterWaitingAcquireLocksClosesTheUndeliveredLease() = withLockRoot { rootPath ->
        val accountId = UserId("cancelled-after-waiting-lock", "example.com")
        val fixtureOwner = AppleAccountRuntimeOwner(rootPath)
        createLockFile(fixtureOwner, accountId)
        val process = AccountLockProcess.start(fixtureOwner.lockFilePath(accountId))
        val cancellation = Job(coroutineContext[Job])
        val acquisitionScope = CoroutineScope(coroutineContext + cancellation)
        var lockWasAcquired = false
        val owner = AppleAccountRuntimeOwner(rootPath) {
            lockWasAcquired = true
            cancellation.cancel()
        }

        try {
            val attempt = acquisitionScope.async {
                owner.acquire(accountId)
            }
            runCurrent()
            assertFalse(attempt.isCompleted)

            process.stop()
            advanceUntilIdle()
            attempt.join()

            assertTrue(lockWasAcquired)
            assertTrue(attempt.isCancelled)
            fixtureOwner.acquireLease(accountId).release()
        } finally {
            process.stop()
        }
    }

    @Test
    fun twoProcessesCannotHoldTheSameAccountLock() = withLockRoot { rootPath ->
        val accountId = UserId("same-account", "example.com")
        val owner = AppleAccountRuntimeOwner(rootPath)
        createLockFile(owner, accountId)
        val process = AccountLockProcess.start(owner.lockFilePath(accountId))

        try {
            assertEquals(
                Either.Left(AccountRuntimeFailure.BUSY),
                owner.tryAcquire(accountId),
            )
        } finally {
            process.stop()
        }

        owner.acquireLease(accountId).release()
    }

    @Test
    fun differentAccountsAcquireIndependentlyAcrossProcesses() = withLockRoot { rootPath ->
        val firstAccount = UserId("first-account", "example.com")
        val secondAccount = UserId("second-account", "example.com")
        val owner = AppleAccountRuntimeOwner(rootPath)
        createLockFile(owner, firstAccount)
        val process = AccountLockProcess.start(owner.lockFilePath(firstAccount))

        try {
            owner.acquireLease(secondAccount).release()
        } finally {
            process.stop()
        }
    }

    @Test
    fun tryAcquireReturnsImmediatelyWhenAnotherProcessOwnsTheAccount() = withLockRoot { rootPath ->
        val accountId = UserId("busy-account", "example.com")
        val owner = AppleAccountRuntimeOwner(rootPath)
        createLockFile(owner, accountId)
        val process = AccountLockProcess.start(owner.lockFilePath(accountId))

        try {
            val started = TimeSource.Monotonic.markNow()
            val result = owner.tryAcquire(accountId)

            assertEquals(
                Either.Left(AccountRuntimeFailure.BUSY),
                result,
            )
            assertTrue(started.elapsedNow() < 250.milliseconds)
        } finally {
            process.stop()
        }
    }

    @Test
    fun waitingAcquireCompletesAfterTheOtherProcessReleases() = withLockRoot { rootPath ->
        val accountId = UserId("waiting-account", "example.com")
        val owner = AppleAccountRuntimeOwner(rootPath)
        createLockFile(owner, accountId)
        val process = AccountLockProcess.start(owner.lockFilePath(accountId))

        try {
            val waiting = async { owner.acquire(accountId) }
            runCurrent()
            assertFalse(waiting.isCompleted)

            process.stop()
            advanceUntilIdle()
            acquiredLease(waiting.await()).release()
        } finally {
            process.stop()
        }
    }

    @Test
    fun cancellingWaitingAcquireDoesNotLeaveOwnershipBehind() = withLockRoot { rootPath ->
        val accountId = UserId("cancelled-account", "example.com")
        val owner = AppleAccountRuntimeOwner(rootPath)
        createLockFile(owner, accountId)
        val process = AccountLockProcess.start(owner.lockFilePath(accountId))

        try {
            val waiting = async { owner.acquire(accountId) }
            runCurrent()
            assertFalse(waiting.isCompleted)
            waiting.cancelAndJoin()

            process.stop()
            owner.acquireLease(accountId).release()
        } finally {
            process.stop()
        }
    }

    @Test
    fun terminatingTheOwnerProcessReleasesTheLockWithoutDeletingItsFile() = withLockRoot { rootPath ->
        val accountId = UserId("terminated-account", "example.com")
        val owner = AppleAccountRuntimeOwner(rootPath)
        createLockFile(owner, accountId)
        val lockFilePath = owner.lockFilePath(accountId)
        val inodeBeforeTermination = inode(lockFilePath)
        val process = AccountLockProcess.start(lockFilePath)

        process.terminate()

        assertTrue(NSFileManager.defaultManager.fileExistsAtPath(lockFilePath))
        assertEquals(inodeBeforeTermination, inode(lockFilePath))
        owner.acquireLease(accountId).release()
    }

    @Test
    fun endingAndReopeningAnAccountReusesTheSameLockFile() = withLockRoot { rootPath ->
        val accountId = UserId("reused-account", "example.com")
        val owner = AppleAccountRuntimeOwner(rootPath)
        val lockFilePath = owner.lockFilePath(accountId)

        owner.acquireLease(accountId).release()
        val firstInode = inode(lockFilePath)
        owner.acquireLease(accountId).release()

        assertEquals(firstInode, inode(lockFilePath))
    }

    @Test
    fun releasingAnOldLeaseTwiceCannotReleaseANewerLease() = withLockRoot { rootPath ->
        val accountId = UserId("double-release-account", "example.com")
        val owner = AppleAccountRuntimeOwner(rootPath)
        val firstLease = owner.acquireLease(accountId)
        firstLease.release()
        val secondLease = owner.acquireLease(accountId)

        firstLease.release()

        assertEquals(
            Either.Left(AccountRuntimeFailure.BUSY),
            owner.tryAcquire(accountId),
        )
        secondLease.release()
        owner.acquireLease(accountId).release()
    }

    private fun withLockRoot(test: suspend kotlinx.coroutines.test.TestScope.(String) -> Unit) = runTest {
        val rootPath = "${NSTemporaryDirectory()}/kalium-account-lock-${NSUUID().UUIDString}"
        check(NSFileManager.defaultManager.createDirectoryAtPath(rootPath, true, null, null))
        try {
            test(rootPath)
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(rootPath, null)
        }
    }

    private suspend fun createLockFile(owner: AppleAccountRuntimeOwner, accountId: UserId) {
        owner.acquireLease(accountId).release()
    }

    private suspend fun AppleAccountRuntimeOwner.acquireLease(accountId: UserId): AccountRuntimeLease =
        acquiredLease(acquire(accountId))

    private fun acquiredLease(acquisition: Either<AccountRuntimeFailure, AccountRuntimeLease>): AccountRuntimeLease =
        assertIs<Either.Right<AccountRuntimeLease>>(acquisition).value

    private fun inode(path: String): ULong = memScoped {
        val fileInfo = alloc<stat>()
        check(stat(path, fileInfo.ptr) == 0)
        fileInfo.st_ino
    }
}
