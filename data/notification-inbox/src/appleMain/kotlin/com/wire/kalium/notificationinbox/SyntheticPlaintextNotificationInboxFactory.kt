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

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.JournalMode
import co.touchlab.sqliter.SynchronousFlag
import co.touchlab.sqliter.interop.Logger
import com.wire.kalium.notificationinbox.db.NotificationInboxDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSFileManager

/**
 * Plaintext Apple persistence strictly for synthetic Milestone 6 probes.
 *
 * This factory accepts only the fixed synthetic account/client scope exported by this module, and
 * raw writes must declare the `SYNTHETIC_FEASIBILITY` delivery source. It intentionally permits
 * structurally valid synthetic JSON envelopes and protobuf bytes unchanged so the exact-blob
 * contract can be probed. Code cannot infer whether arbitrary bytes came from a backend, so the
 * unavailable production constructor—not a content heuristic—is the production safety boundary. It records
 * `PLAINTEXT_SYNTHETIC_SPIKE_V1` in contract metadata. The native driver does not provide SQLCipher,
 * Keychain key distribution, file protection, or no-follow path opening. Consequently callers are
 * responsible for supplying locally constructed synthetic bytes only; this type must never receive
 * backend events, real account identifiers, or real decrypted protobufs and is not a production
 * construction boundary.
 *
 * The caller must own the Milestone 5 process lock. DELETE journaling, zero busy timeout, one reader,
 * foreign keys, and EXTRA synchronous writes keep the synthetic behavior bounded and conservative;
 * they do not close the encryption or path-security gates.
 */
public class SyntheticPlaintextNotificationInboxFactory(
    private val directoryPath: String,
    private val limits: NotificationInboxLimits
) {
    public suspend fun open(): SyntheticNotificationInboxOpenResult =
        open(SyntheticNotificationInboxFailurePoint.NONE)

    /** One-shot synthetic rollback proof; unavailable to any future secure factory. */
    public suspend fun openWithFailureBeforeCursorUpsert(): SyntheticNotificationInboxOpenResult =
        open(SyntheticNotificationInboxFailurePoint.BEFORE_CURSOR_UPSERT)

    /** One-shot synthetic rollback proof; unavailable to any future secure factory. */
    public suspend fun openWithFailureBeforeParentReceiveComplete(): SyntheticNotificationInboxOpenResult =
        open(SyntheticNotificationInboxFailurePoint.BEFORE_PARENT_RECEIVE_COMPLETE)

    @Suppress("LongMethod", "ReturnCount")
    private suspend fun open(
        failurePoint: SyntheticNotificationInboxFailurePoint
    ): SyntheticNotificationInboxOpenResult {
        if (!directoryPath.isSafeAbsoluteDirectoryPath()) {
            return SyntheticNotificationInboxOpenResult.Failure(NotificationInboxFailure.INVALID_INPUT)
        }
        val created = NSFileManager.defaultManager.createDirectoryAtPath(
            path = directoryPath,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        if (!created) {
            return SyntheticNotificationInboxOpenResult.Failure(NotificationInboxFailure.STORAGE_UNAVAILABLE)
        }

        val driver = try {
            NativeSqliteDriver(
                configuration = DatabaseConfiguration(
                    name = SYNTHETIC_DATABASE_NAME,
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
                    loggingConfig = DatabaseConfiguration.Logging(logger = PayloadSafeSilentLogger),
                    extendedConfig = DatabaseConfiguration.Extended(
                        foreignKeyConstraints = true,
                        busyTimeout = NO_BUSY_WAIT_MILLIS,
                        basePath = directoryPath,
                        synchronousFlag = SynchronousFlag.EXTRA
                    )
                ),
                maxReaderConnections = SINGLE_READER_CONNECTION
            )
        } catch (_: Throwable) {
            return SyntheticNotificationInboxOpenResult.Failure(NotificationInboxFailure.STORAGE_UNAVAILABLE)
        }

        val store = SqlDelightNotificationInboxStore(
            driver = driver,
            dispatcher = Dispatchers.Default,
            limits = limits,
            syntheticOnly = true,
            expectedStorageProfile = SYNTHETIC_PLAINTEXT_STORAGE_PROFILE,
            syntheticFailurePoint = failurePoint
        )
        val failure = try {
            store.validateCompatibility()
        } catch (cancellation: CancellationException) {
            store.close()
            throw cancellation
        }
        return if (failure == null) {
            SyntheticNotificationInboxOpenResult.Opened(store)
        } else {
            store.close()
            SyntheticNotificationInboxOpenResult.Failure(failure)
        }
    }
}

public sealed interface SyntheticNotificationInboxOpenResult {
    public data class Opened(public val store: NotificationInboxStore) : SyntheticNotificationInboxOpenResult
    public data class Failure(public val reason: NotificationInboxFailure) : SyntheticNotificationInboxOpenResult
}

private object PayloadSafeSilentLogger : Logger {
    override fun trace(message: String): Unit = Unit
    override val vActive: Boolean = false
    override fun vWrite(message: String): Unit = Unit
    override val eActive: Boolean = false
    override fun eWrite(message: String, exception: Throwable?): Unit = Unit
}

private fun String.isSafeAbsoluteDirectoryPath(): Boolean =
    startsWith(PATH_SEPARATOR) && length in 2..MAX_PATH_LENGTH && !endsWith(PATH_SEPARATOR) &&
            indexOf(NULL_CHARACTER) < 0 && drop(1).split(PATH_SEPARATOR).all { component ->
                component.isNotEmpty() && component != CURRENT_DIRECTORY && component != PARENT_DIRECTORY
            }

private const val SYNTHETIC_DATABASE_NAME = "synthetic-notification-inbox.sqlite"
private const val PATH_SEPARATOR = "/"
private const val CURRENT_DIRECTORY = "."
private const val PARENT_DIRECTORY = ".."
private const val NULL_CHARACTER = '\u0000'
private const val MAX_PATH_LENGTH = 4_096
private const val NO_BUSY_WAIT_MILLIS = 0
private const val SINGLE_READER_CONNECTION = 1
