/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.wire.kalium.notificationinbox

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.ENOENT
import platform.posix.S_IFMT
import platform.posix.S_IFREG
import platform.posix.S_IWGRP
import platform.posix.S_IWOTH
import platform.posix.errno
import platform.posix.geteuid
import platform.posix.lstat
import platform.posix.stat

/**
 * Synthetic-only physical footprint probe for the exact SQLite database and all standard sidecars.
 *
 * Measurement must occur while the Milestone 5 account lock is held and the store is closed or at
 * a caller-approved safe point. This path-based Apple probe rejects symbolic links, directories,
 * foreign ownership, multi-link files, and group/other writes, but it does not replace the future
 * production factory's descriptor-relative App Group ancestry validation.
 */
public class SyntheticNotificationInboxStorageMeasurer(
    private val directoryPath: String
) {
    @Suppress("ReturnCount")
    public fun measure(): InboxReadResult<NotificationInboxStorageFootprint> {
        if (!directoryPath.isSafeSyntheticMeasurementPath()) {
            return InboxReadResult.StorageFailure(NotificationInboxFailure.INVALID_INPUT)
        }
        val measured = LongArray(STORAGE_FILE_NAMES.size)
        for ((index, fileName) in STORAGE_FILE_NAMES.withIndex()) {
            when (val result = measureRegularFile("$directoryPath/$fileName")) {
                is SyntheticFileMeasurement.Bytes -> measured[index] = result.value
                SyntheticFileMeasurement.Missing -> measured[index] = 0L
                SyntheticFileMeasurement.Unsafe ->
                    return InboxReadResult.StorageFailure(NotificationInboxFailure.CORRUPT_STATE)
            }
        }
        return InboxReadResult.Success(
            NotificationInboxStorageFootprint(
                databaseBytes = measured[DATABASE_INDEX],
                walBytes = measured[WAL_INDEX],
                sharedMemoryBytes = measured[SHM_INDEX],
                rollbackJournalBytes = measured[JOURNAL_INDEX]
            )
        )
    }
}

private fun measureRegularFile(path: String): SyntheticFileMeasurement = memScoped {
    val metadata = alloc<stat>()
    if (lstat(path, metadata.ptr) != 0) {
        return@memScoped if (errno == ENOENT) SyntheticFileMeasurement.Missing else SyntheticFileMeasurement.Unsafe
    }
    val mode = metadata.st_mode.toInt()
    val safe = mode and S_IFMT == S_IFREG && metadata.st_uid == geteuid() && metadata.st_nlink.toLong() == 1L &&
            mode and (S_IWGRP or S_IWOTH) == 0 && metadata.st_size >= 0
    if (safe) SyntheticFileMeasurement.Bytes(metadata.st_size) else SyntheticFileMeasurement.Unsafe
}

private fun String.isSafeSyntheticMeasurementPath(): Boolean =
    startsWith(PATH_SEPARATOR) && length in 2..MAX_MEASUREMENT_PATH_LENGTH && !endsWith(PATH_SEPARATOR) &&
            indexOf(NULL_CHARACTER) < 0 && drop(1).split(PATH_SEPARATOR).all { component ->
                component.isNotEmpty() && component != CURRENT_DIRECTORY && component != PARENT_DIRECTORY
            }

private sealed interface SyntheticFileMeasurement {
    public data class Bytes(val value: Long) : SyntheticFileMeasurement
    public data object Missing : SyntheticFileMeasurement
    public data object Unsafe : SyntheticFileMeasurement
}

private const val SYNTHETIC_DATABASE_NAME_V2 = "synthetic-notification-inbox.sqlite"
private val STORAGE_FILE_NAMES: List<String> = listOf(
    SYNTHETIC_DATABASE_NAME_V2,
    "$SYNTHETIC_DATABASE_NAME_V2-wal",
    "$SYNTHETIC_DATABASE_NAME_V2-shm",
    "$SYNTHETIC_DATABASE_NAME_V2-journal"
)
private const val DATABASE_INDEX = 0
private const val WAL_INDEX = 1
private const val SHM_INDEX = 2
private const val JOURNAL_INDEX = 3
private const val PATH_SEPARATOR = "/"
private const val CURRENT_DIRECTORY = "."
private const val PARENT_DIRECTORY = ".."
private const val NULL_CHARACTER = '\u0000'
private const val MAX_MEASUREMENT_PATH_LENGTH = 4_096
