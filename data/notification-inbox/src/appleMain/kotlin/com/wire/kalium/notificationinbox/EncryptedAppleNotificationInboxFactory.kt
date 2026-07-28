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
@file:Suppress("TooManyFunctions")

package com.wire.kalium.notificationinbox

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.JournalMode
import co.touchlab.sqliter.SynchronousFlag
import co.touchlab.sqliter.interop.Logger
import com.wire.kalium.notificationinbox.db.NotificationInboxDatabase
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import platform.posix.AT_SYMLINK_NOFOLLOW
import platform.posix.EEXIST
import platform.posix.ENOENT
import platform.posix.F_GETPROTECTIONCLASS
import platform.posix.F_SETPROTECTIONCLASS
import platform.posix.O_CLOEXEC
import platform.posix.O_DIRECTORY
import platform.posix.O_NOFOLLOW_ANY
import platform.posix.O_NONBLOCK
import platform.posix.O_RDONLY
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.S_IFREG
import platform.posix.S_IWGRP
import platform.posix.S_IWOTH
import platform.posix.close
import platform.posix.errno
import platform.posix.fchmod
import platform.posix.fcntl
import platform.posix.fstat
import platform.posix.fstatat
import platform.posix.geteuid
import platform.posix.mkdirat
import platform.posix.open
import platform.posix.stat

/**
 * SQLCipher-backed Apple production handoff store.
 *
 * The caller supplies an independent random key read from the shared Keychain while holding the
 * account process lock. SQLiter applies it before schema/version access on every connection.
 * Construction additionally requires a non-empty `PRAGMA cipher_version`; system SQLite therefore
 * fails closed instead of silently accepting its no-op `PRAGMA key`.
 *
 * The database lives below a digest-scoped App Group path. Account and client identifiers never
 * appear in a file name. Cursor cutover is foreground-owned and is intentionally not performed by
 * this factory.
 */
public class EncryptedAppleNotificationInboxFactory(
    private val sharedAppGroupRoot: String,
    private val scope: InboxScope,
    private val key: String,
    private val limits: NotificationInboxLimits
) {
    /** Opaque digest-scoped path, exposed for native file-protection and encryption verification. */
    public val databaseFilePath: String
        get() = "${productionDirectoryPath(sharedAppGroupRoot, scope)}/$PRODUCTION_DATABASE_NAME"

    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount", "TooGenericExceptionCaught")
    public suspend fun open(): EncryptedNotificationInboxOpenResult {
        if (!sharedAppGroupRoot.isSafeAbsoluteDirectoryPath() || !scope.isValidFactoryScope() || !key.isValidKey()) {
            return EncryptedNotificationInboxOpenResult.Failure(NotificationInboxFailure.INVALID_INPUT)
        }
        val directoryPath = productionDirectoryPath(sharedAppGroupRoot, scope)
        val hardenedDirectory = HardenedInboxDirectory.openOrCreate(
            rootPath = sharedAppGroupRoot,
            directoryPath = directoryPath,
            components = productionPathComponents(scope)
        ) ?: run {
            return EncryptedNotificationInboxOpenResult.Failure(NotificationInboxFailure.STORAGE_UNAVAILABLE)
        }

        try {
            val driver = try {
                // Reject any pre-existing substituted database or sidecar before SQLiter sees it.
                if (!hardenedDirectory.hardenDatabaseFiles()) {
                    return EncryptedNotificationInboxOpenResult.Failure(NotificationInboxFailure.STORAGE_UNAVAILABLE)
                }
                NativeSqliteDriver(
                    configuration = DatabaseConfiguration(
                        name = PRODUCTION_DATABASE_NAME,
                        version = NotificationInboxDatabase.Schema.version.toInt(),
                        journalMode = JournalMode.DELETE,
                        create = { connection ->
                            wrapConnection(connection) { NotificationInboxDatabase.Schema.synchronous().create(it) }
                        },
                        upgrade = { connection, oldVersion, newVersion ->
                            wrapConnection(connection) {
                                NotificationInboxDatabase.Schema.synchronous().migrate(
                                    it,
                                    oldVersion.toLong(),
                                    newVersion.toLong()
                                )
                            }
                        },
                        loggingConfig = DatabaseConfiguration.Logging(logger = EncryptedStoreSilentLogger),
                        extendedConfig = DatabaseConfiguration.Extended(
                            foreignKeyConstraints = true,
                            busyTimeout = NO_BUSY_WAIT_MILLIS,
                            basePath = directoryPath,
                            synchronousFlag = SynchronousFlag.EXTRA
                        ),
                        encryptionConfig = DatabaseConfiguration.Encryption(key = key)
                    ),
                    maxReaderConnections = SINGLE_READER_CONNECTION
                )
            } catch (_: Throwable) {
                return EncryptedNotificationInboxOpenResult.Failure(NotificationInboxFailure.STORAGE_UNAVAILABLE)
            }

            if (!hardenedDirectory.hardenDatabaseFiles()) {
                driver.close()
                return EncryptedNotificationInboxOpenResult.Failure(NotificationInboxFailure.STORAGE_UNAVAILABLE)
            }

            val cipherVersion = try {
                driver.readCipherVersion()
            } catch (cancellation: CancellationException) {
                driver.close()
                throw cancellation
            } catch (_: Throwable) {
                driver.close()
                return EncryptedNotificationInboxOpenResult.Failure(NotificationInboxFailure.STORAGE_UNAVAILABLE)
            }
            if (cipherVersion.isNullOrBlank()) {
                driver.close()
                return EncryptedNotificationInboxOpenResult.Failure(NotificationInboxFailure.INCOMPATIBLE_SCHEMA)
            }

            val store = SqlDelightNotificationInboxStore(
                driver = driver,
                dispatcher = Dispatchers.Default,
                limits = limits,
                syntheticOnly = false,
                expectedStorageProfile = ENCRYPTED_STORAGE_PROFILE,
                syntheticFailurePoint = SyntheticNotificationInboxFailurePoint.NONE
            )
            val failure = try {
                store.validateCompatibility()
            } catch (cancellation: CancellationException) {
                store.close()
                throw cancellation
            }
            return if (failure == null && hardenedDirectory.hardenDatabaseFiles()) {
                EncryptedNotificationInboxOpenResult.Opened(store)
            } else {
                store.close()
                EncryptedNotificationInboxOpenResult.Failure(
                    failure ?: NotificationInboxFailure.STORAGE_UNAVAILABLE
                )
            }
        } finally {
            hardenedDirectory.close()
        }
    }
}

public sealed interface EncryptedNotificationInboxOpenResult {
    public data class Opened(public val store: NotificationInboxStore) : EncryptedNotificationInboxOpenResult
    public data class Failure(public val reason: NotificationInboxFailure) : EncryptedNotificationInboxOpenResult
}

private suspend fun SqlDriver.readCipherVersion(): String? =
    executeQuery(
        identifier = null,
        sql = "PRAGMA cipher_version;",
        mapper = { cursor ->
            val value = if (cursor.next().value) cursor.getString(0) else null
            QueryResult.Value(value)
        },
        parameters = 0
    ).value

private fun productionDirectoryPath(root: String, scope: InboxScope): String {
    return "$root/${productionPathComponents(scope).joinToString(PATH_SEPARATOR)}"
}

private fun productionPathComponents(scope: InboxScope): List<String> = listOf(
    PRODUCT_DIRECTORY,
    VERSION_DIRECTORY,
    sha256LowercaseHex("${scope.accountId}\u0000${scope.clientId}".encodeToByteArray()),
    HANDOFF_DIRECTORY
)

/**
 * Owns a descriptor for the final handoff directory while SQLiter opens its path.
 *
 * Darwin's macOS Kotlin bindings omit `openat`, so creation and entry identity checks are
 * descriptor-relative (`mkdirat`/`fstatat`) while every absolute open uses `O_NOFOLLOW_ANY`.
 * Comparing the opened inode with the parent-relative entry prevents a renamed or substituted
 * ancestry from being accepted. iOS and macOS therefore share one fail-closed implementation.
 */
internal class HardenedInboxDirectory private constructor(
    private val descriptor: Int,
    private val path: String
) {
    fun revalidatePathIdentity(): Boolean {
        val reopenedDescriptor = openDirectoryNoFollow(path)
        if (reopenedDescriptor < 0) return false
        return try {
            sameFileIdentity(descriptor, reopenedDescriptor) &&
                    validateDirectory(reopenedDescriptor, requirePrivateMode = true) &&
                    hasRequiredProtectionClass(reopenedDescriptor)
        } finally {
            closeOnce(reopenedDescriptor)
        }
    }

    fun hardenDatabaseFiles(): Boolean {
        var isSafe = revalidatePathIdentity()
        if (isSafe) {
            for (fileName in DATABASE_FILE_NAMES) {
                if (hardenRegularFile(descriptor, path, fileName) == FileHardeningResult.UNSAFE) {
                    isSafe = false
                    break
                }
            }
        }
        return isSafe && revalidatePathIdentity()
    }

    fun close() {
        closeOnce(descriptor)
    }

    companion object {
        @Suppress("ReturnCount")
        fun openOrCreate(
            rootPath: String,
            directoryPath: String,
            components: List<String>
        ): HardenedInboxDirectory? {
            var parentDescriptor = openDirectoryNoFollow(rootPath)
            if (parentDescriptor < 0) return null
            if (!validateDirectory(parentDescriptor, requirePrivateMode = false)) {
                closeOnce(parentDescriptor)
                return null
            }

            var parentPath = rootPath
            for (component in components) {
                if (mkdirat(parentDescriptor, component, PRIVATE_DIRECTORY_MODE.toUShort()) != 0 && errno != EEXIST) {
                    closeOnce(parentDescriptor)
                    return null
                }
                val childPath = "$parentPath/$component"
                val childDescriptor = openDirectoryNoFollow(childPath)
                if (childDescriptor < 0) {
                    closeOnce(parentDescriptor)
                    return null
                }
                val validChild = validateDirectory(childDescriptor, requirePrivateMode = true) &&
                        descriptorMatchesRelativeEntry(parentDescriptor, component, childDescriptor) &&
                        applyAndVerifyProtectionClass(childDescriptor)
                closeOnce(parentDescriptor)
                if (!validChild) {
                    closeOnce(childDescriptor)
                    return null
                }
                parentDescriptor = childDescriptor
                parentPath = childPath
            }

            if (parentPath != directoryPath) {
                closeOnce(parentDescriptor)
                return null
            }
            return HardenedInboxDirectory(parentDescriptor, parentPath)
        }
    }
}

private fun openDirectoryNoFollow(path: String): Int =
    open(path, O_RDONLY or O_DIRECTORY or O_NOFOLLOW_ANY or O_CLOEXEC)

private fun validateDirectory(descriptor: Int, requirePrivateMode: Boolean): Boolean = memScoped {
    val metadata = alloc<stat>()
    if (fstat(descriptor, metadata.ptr) != 0) return@memScoped false
    val mode = metadata.st_mode.toInt()
    val safeIdentity = mode and S_IFMT == S_IFDIR &&
            metadata.st_uid == geteuid() &&
            metadata.st_nlink.toLong() > 0L
    val safePermissions = mode and (S_IWGRP or S_IWOTH) == 0 &&
            (!requirePrivateMode || mode and FILE_PERMISSION_MASK == PRIVATE_DIRECTORY_MODE)
    safeIdentity && safePermissions
}

private fun descriptorMatchesRelativeEntry(
    parentDescriptor: Int,
    name: String,
    childDescriptor: Int
): Boolean = memScoped {
    val relativeMetadata = alloc<stat>()
    val childMetadata = alloc<stat>()
    if (fstatat(parentDescriptor, name, relativeMetadata.ptr, AT_SYMLINK_NOFOLLOW) != 0 ||
        fstat(childDescriptor, childMetadata.ptr) != 0
    ) {
        return@memScoped false
    }
    val relativeMode = relativeMetadata.st_mode.toInt()
    relativeMode and S_IFMT == S_IFDIR &&
            relativeMetadata.st_dev == childMetadata.st_dev &&
            relativeMetadata.st_ino == childMetadata.st_ino
}

private fun sameFileIdentity(leftDescriptor: Int, rightDescriptor: Int): Boolean = memScoped {
    val leftMetadata = alloc<stat>()
    val rightMetadata = alloc<stat>()
    if (fstat(leftDescriptor, leftMetadata.ptr) != 0 || fstat(rightDescriptor, rightMetadata.ptr) != 0) {
        return@memScoped false
    }
    leftMetadata.st_dev == rightMetadata.st_dev && leftMetadata.st_ino == rightMetadata.st_ino
}

private fun hardenRegularFile(
    directoryDescriptor: Int,
    directoryPath: String,
    name: String
): FileHardeningResult {
    val fileDescriptor = open(
        "$directoryPath/$name",
        O_RDONLY or O_NONBLOCK or O_NOFOLLOW_ANY or O_CLOEXEC
    )
    if (fileDescriptor < 0) {
        return if (errno == ENOENT) FileHardeningResult.MISSING else FileHardeningResult.UNSAFE
    }
    return try {
        if (!validateRegularFileIdentity(directoryDescriptor, name, fileDescriptor)) {
            FileHardeningResult.UNSAFE
        } else if (fchmod(fileDescriptor, PRIVATE_FILE_MODE.toUShort()) != 0) {
            FileHardeningResult.UNSAFE
        } else if (!applyAndVerifyProtectionClass(fileDescriptor)) {
            FileHardeningResult.UNSAFE
        } else if (!validateHardenedRegularFile(directoryDescriptor, name, fileDescriptor)) {
            FileHardeningResult.UNSAFE
        } else {
            FileHardeningResult.HARDENED
        }
    } finally {
        closeOnce(fileDescriptor)
    }
}

private fun validateRegularFileIdentity(
    directoryDescriptor: Int,
    name: String,
    fileDescriptor: Int
): Boolean = memScoped {
    val relativeMetadata = alloc<stat>()
    val fileMetadata = alloc<stat>()
    if (fstatat(directoryDescriptor, name, relativeMetadata.ptr, AT_SYMLINK_NOFOLLOW) != 0 ||
        fstat(fileDescriptor, fileMetadata.ptr) != 0
    ) {
        return@memScoped false
    }
    val mode = fileMetadata.st_mode.toInt()
    val relativeMode = relativeMetadata.st_mode.toInt()
    mode and S_IFMT == S_IFREG &&
            relativeMode and S_IFMT == S_IFREG &&
            fileMetadata.st_uid == geteuid() &&
            fileMetadata.st_nlink.toLong() == SINGLE_LINK_COUNT &&
            relativeMetadata.st_dev == fileMetadata.st_dev &&
            relativeMetadata.st_ino == fileMetadata.st_ino
}

private fun validateHardenedRegularFile(
    directoryDescriptor: Int,
    name: String,
    fileDescriptor: Int
): Boolean = memScoped {
    val metadata = alloc<stat>()
    if (!validateRegularFileIdentity(directoryDescriptor, name, fileDescriptor) ||
        fstat(fileDescriptor, metadata.ptr) != 0
    ) {
        return@memScoped false
    }
    val mode = metadata.st_mode.toInt()
    mode and FILE_PERMISSION_MASK == PRIVATE_FILE_MODE &&
            mode and (S_IWGRP or S_IWOTH) == 0 &&
            hasRequiredProtectionClass(fileDescriptor)
}

private fun applyAndVerifyProtectionClass(descriptor: Int): Boolean =
    fcntl(descriptor, F_SETPROTECTIONCLASS, COMPLETE_UNTIL_FIRST_AUTHENTICATION_CLASS) == 0 &&
            hasRequiredProtectionClass(descriptor)

private fun hasRequiredProtectionClass(descriptor: Int): Boolean =
    fcntl(descriptor, F_GETPROTECTIONCLASS) == COMPLETE_UNTIL_FIRST_AUTHENTICATION_CLASS

private fun closeOnce(descriptor: Int) {
    if (descriptor >= 0) close(descriptor)
}

private enum class FileHardeningResult {
    HARDENED,
    MISSING,
    UNSAFE
}

private fun String.isSafeAbsoluteDirectoryPath(): Boolean =
    startsWith(PATH_SEPARATOR) && length in 2..MAX_PATH_LENGTH && !endsWith(PATH_SEPARATOR) &&
            indexOf(NULL_CHARACTER) < 0 && drop(1).split(PATH_SEPARATOR).all { component ->
                component.isNotEmpty() && component != CURRENT_DIRECTORY && component != PARENT_DIRECTORY
            }

private fun InboxScope.isValidFactoryScope(): Boolean =
    accountId.isNotBlank() && clientId.isNotBlank() &&
            accountId.encodeToByteArray().size <= MAX_FACTORY_IDENTIFIER_BYTES &&
            clientId.encodeToByteArray().size <= MAX_FACTORY_IDENTIFIER_BYTES &&
            accountId.indexOf(NULL_CHARACTER) < 0 && clientId.indexOf(NULL_CHARACTER) < 0

private fun String.isValidKey(): Boolean =
    length in MIN_KEY_CHARACTERS..MAX_KEY_CHARACTERS && indexOf(NULL_CHARACTER) < 0

private object EncryptedStoreSilentLogger : Logger {
    override fun trace(message: String): Unit = Unit
    override val vActive: Boolean = false
    override fun vWrite(message: String): Unit = Unit
    override val eActive: Boolean = false
    override fun eWrite(message: String, exception: Throwable?): Unit = Unit
}

private const val PRODUCTION_DATABASE_NAME = "notification-inbox.sqlite"
private const val PRODUCT_DIRECTORY = "kalium-nse"
private const val VERSION_DIRECTORY = "v1"
private const val HANDOFF_DIRECTORY = "handoff"
private const val ENCRYPTED_STORAGE_PROFILE = "ENCRYPTED_AT_REST_V1"
private val DATABASE_FILE_NAMES: List<String> = listOf(
    PRODUCTION_DATABASE_NAME,
    "$PRODUCTION_DATABASE_NAME-wal",
    "$PRODUCTION_DATABASE_NAME-shm",
    "$PRODUCTION_DATABASE_NAME-journal"
)
private const val PATH_SEPARATOR = "/"
private const val CURRENT_DIRECTORY = "."
private const val PARENT_DIRECTORY = ".."
private const val NULL_CHARACTER = '\u0000'
private const val FILE_PERMISSION_MASK = 0x1FF
private const val PRIVATE_DIRECTORY_MODE = 0x1C0
private const val PRIVATE_FILE_MODE = 0x180
private const val SINGLE_LINK_COUNT = 1L

// Darwin class C, surfaced by Foundation as NSFileProtectionCompleteUntilFirstUserAuthentication.
private const val COMPLETE_UNTIL_FIRST_AUTHENTICATION_CLASS = 3
private const val MAX_PATH_LENGTH = 4_096
private const val MAX_FACTORY_IDENTIFIER_BYTES = 1_024
private const val MIN_KEY_CHARACTERS = 32
private const val MAX_KEY_CHARACTERS = 1_024
private const val NO_BUSY_WAIT_MILLIS = 0
private const val SINGLE_READER_CONNECTION = 1
