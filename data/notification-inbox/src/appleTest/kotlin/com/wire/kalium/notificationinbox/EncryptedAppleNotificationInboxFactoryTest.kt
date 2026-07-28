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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.wire.kalium.notificationinbox

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSFileManager
import platform.posix.F_GETPROTECTIONCLASS
import platform.posix.F_SETPROTECTIONCLASS
import platform.posix.O_CLOEXEC
import platform.posix.O_CREAT
import platform.posix.O_DIRECTORY
import platform.posix.O_NOFOLLOW_ANY
import platform.posix.O_RDONLY
import platform.posix.O_RDWR
import platform.posix.S_IFMT
import platform.posix.S_IFREG
import platform.posix.close
import platform.posix.fchmod
import platform.posix.fcntl
import platform.posix.fstat
import platform.posix.open
import platform.posix.rename
import platform.posix.stat
import platform.posix.symlink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class EncryptedAppleNotificationInboxFactoryTest {
    @Test
    fun givenSharedRootIsSymlink_whenOpening_thenStorageFailsClosed() = runBlocking {
        val target = uniqueTestPath("target")
        val linkedRoot = uniqueTestPath("linked-root")
        try {
            assertTrue(createPrivateDirectory(target))
            assertEquals(0, symlink(target, linkedRoot))

            val result = factory(linkedRoot).open()

            assertStorageUnavailable(result)
        } finally {
            removeIfPresent(linkedRoot)
            removeIfPresent(target)
        }
    }

    @Test
    fun givenDigestDirectoryIsSymlink_whenOpening_thenStorageFailsClosed() = runBlocking {
        val root = uniqueTestPath("root")
        val externalDirectory = uniqueTestPath("external")
        val factory = factory(root)
        val handoffDirectory = factory.databaseFilePath.substringBeforeLast(PATH_SEPARATOR)
        val digestDirectory = handoffDirectory.substringBeforeLast(PATH_SEPARATOR)
        val versionDirectory = digestDirectory.substringBeforeLast(PATH_SEPARATOR)
        try {
            assertTrue(createPrivateDirectory(root))
            assertTrue(createPrivateDirectory("$root/kalium-nse"))
            assertTrue(createPrivateDirectory(versionDirectory))
            assertTrue(createPrivateDirectory(externalDirectory))
            assertEquals(0, symlink(externalDirectory, digestDirectory))

            val result = factory.open()

            assertStorageUnavailable(result)
        } finally {
            removeIfPresent(root)
            removeIfPresent(externalDirectory)
        }
    }

    @Test
    fun givenDatabaseIsSymlink_whenOpening_thenSqliterNeverAcceptsIt() = runBlocking {
        val root = uniqueTestPath("root")
        val externalFile = uniqueTestPath("external-file")
        val factory = factory(root)
        val handoffDirectory = factory.databaseFilePath.substringBeforeLast(PATH_SEPARATOR)
        try {
            assertTrue(createPrivateTree(root, handoffDirectory))
            assertTrue(createRegularFile(externalFile))
            assertEquals(0, symlink(externalFile, factory.databaseFilePath))

            val result = factory.open()

            assertStorageUnavailable(result)
        } finally {
            removeIfPresent(root)
            removeIfPresent(externalFile)
        }
    }

    @Test
    fun givenExistingPrivateDatabase_whenOpening_thenModeAndProtectionClassAreVerified() = runBlocking {
        val root = uniqueTestPath("root")
        val factory = factory(root)
        val handoffDirectory = factory.databaseFilePath.substringBeforeLast(PATH_SEPARATOR)
        try {
            assertTrue(createPrivateTree(root, handoffDirectory))
            assertTrue(createRegularFile(factory.databaseFilePath, mode = PERMISSIVE_FILE_MODE))

            when (val result = factory.open()) {
                is EncryptedNotificationInboxOpenResult.Opened -> result.store.close()
                is EncryptedNotificationInboxOpenResult.Failure -> Unit
            }

            verifyHardenedRegularFile(factory.databaseFilePath)
        } finally {
            removeIfPresent(root)
        }
    }

    @Test
    fun givenProtectedDirectory_whenFileIsCreated_thenClassCIsInheritedBeforeExplicitFileHardening() {
        val directory = uniqueTestPath("protected-directory")
        val file = "$directory/inherited"
        try {
            assertTrue(createPrivateDirectory(directory))
            val directoryDescriptor = open(
                directory,
                O_RDONLY or O_DIRECTORY or O_NOFOLLOW_ANY or O_CLOEXEC
            )
            assertTrue(directoryDescriptor >= 0)
            try {
                assertEquals(
                    0,
                    fcntl(
                        directoryDescriptor,
                        F_SETPROTECTIONCLASS,
                        COMPLETE_UNTIL_FIRST_AUTHENTICATION_CLASS
                    )
                )
                assertEquals(
                    COMPLETE_UNTIL_FIRST_AUTHENTICATION_CLASS,
                    fcntl(directoryDescriptor, F_GETPROTECTIONCLASS)
                )
                assertTrue(createRegularFile(file))
                assertEquals(
                    COMPLETE_UNTIL_FIRST_AUTHENTICATION_CLASS,
                    protectionClass(file)
                )
            } finally {
                close(directoryDescriptor)
            }
        } finally {
            removeIfPresent(directory)
        }
    }

    @Test
    fun givenValidatedDirectoryIsReplaced_whenRevalidating_thenIdentityMismatchFailsClosed() {
        val root = uniqueTestPath("root")
        val components = listOf("kalium-nse", "v1", "digest", "handoff")
        val directory = "$root/${components.joinToString(PATH_SEPARATOR)}"
        val movedDirectory = "$root/kalium-nse/v1/digest/handoff-moved"
        try {
            assertTrue(createPrivateDirectory(root))
            val hardenedDirectory = HardenedInboxDirectory.openOrCreate(root, directory, components)
            assertTrue(hardenedDirectory != null)
            try {
                assertEquals(0, rename(directory, movedDirectory))
                assertTrue(createPrivateDirectory(directory))

                assertFalse(hardenedDirectory.revalidatePathIdentity())
            } finally {
                hardenedDirectory.close()
            }
        } finally {
            removeIfPresent(root)
        }
    }
}

private fun factory(root: String): EncryptedAppleNotificationInboxFactory =
    EncryptedAppleNotificationInboxFactory(
        sharedAppGroupRoot = root,
        scope = TEST_SCOPE,
        key = TEST_KEY,
        limits = TEST_LIMITS
    )

private fun assertStorageUnavailable(result: EncryptedNotificationInboxOpenResult) {
    val failure = assertIs<EncryptedNotificationInboxOpenResult.Failure>(result)
    assertEquals(NotificationInboxFailure.STORAGE_UNAVAILABLE, failure.reason)
}

private fun createPrivateTree(root: String, leaf: String): Boolean {
    if (!createPrivateDirectory(root)) return false
    var current = root
    val relativeComponents = leaf.removePrefix("$root/").split(PATH_SEPARATOR)
    for (component in relativeComponents) {
        current = "$current/$component"
        if (!createPrivateDirectory(current)) return false
    }
    return true
}

private fun createPrivateDirectory(path: String): Boolean {
    val manager = NSFileManager.defaultManager
    val created = manager.createDirectoryAtPath(
        path = path,
        withIntermediateDirectories = false,
        attributes = null,
        error = null
    )
    return created && platform.posix.chmod(path, PRIVATE_DIRECTORY_MODE.toUShort()) == 0
}

private fun createRegularFile(path: String, mode: Int = PRIVATE_FILE_MODE): Boolean {
    val descriptor = open(path, O_RDWR or O_CREAT or O_NOFOLLOW_ANY or O_CLOEXEC, mode)
    if (descriptor < 0) return false
    return try {
        fchmod(descriptor, mode.toUShort()) == 0
    } finally {
        close(descriptor)
    }
}

private fun verifyHardenedRegularFile(path: String): Unit = memScoped {
    val descriptor = open(path, O_RDONLY or O_NOFOLLOW_ANY or O_CLOEXEC)
    assertTrue(descriptor >= 0)
    try {
        val metadata = alloc<stat>()
        assertEquals(0, fstat(descriptor, metadata.ptr))
        assertEquals(S_IFREG, metadata.st_mode.toInt() and S_IFMT)
        assertEquals(PRIVATE_FILE_MODE, metadata.st_mode.toInt() and FILE_PERMISSION_MASK)
        assertEquals(SINGLE_LINK_COUNT, metadata.st_nlink.toLong())
        assertEquals(COMPLETE_UNTIL_FIRST_AUTHENTICATION_CLASS, fcntl(descriptor, F_GETPROTECTIONCLASS))
    } finally {
        close(descriptor)
    }
}

private fun protectionClass(path: String): Int {
    val descriptor = open(path, O_RDONLY or O_NOFOLLOW_ANY or O_CLOEXEC)
    assertTrue(descriptor >= 0)
    return try {
        fcntl(descriptor, F_GETPROTECTIONCLASS)
    } finally {
        close(descriptor)
    }
}

private fun uniqueTestPath(suffix: String): String =
    "/private/tmp/kalium-notification-inbox-${Uuid.random()}-$suffix"

private fun removeIfPresent(path: String) {
    NSFileManager.defaultManager.removeItemAtPath(path, error = null)
}

private val TEST_SCOPE = InboxScope("account", "client")
private val TEST_LIMITS = NotificationInboxLimits(
    maxIdentifierUtf8Bytes = 256,
    maxCursorUtf8Bytes = 256,
    maxReasonUtf8Bytes = 256,
    maxRawEnvelopeBytes = 65_536,
    maxDecryptedProtoBytes = 65_536,
    maxBatchBlobBytes = 262_144,
    maxRowsPerRead = 16,
    maxChildrenPerEvent = 8,
    maxRetryCount = 3
)
private const val TEST_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
private const val PATH_SEPARATOR = "/"
private const val FILE_PERMISSION_MASK = 0x1FF
private const val PRIVATE_DIRECTORY_MODE = 0x1C0
private const val PRIVATE_FILE_MODE = 0x180
private const val PERMISSIVE_FILE_MODE = 0x1A4
private const val SINGLE_LINK_COUNT = 1L
private const val COMPLETE_UNTIL_FIRST_AUTHENTICATION_CLASS = 3
