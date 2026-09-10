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
package com.wire.kalium.persistence.db

import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.util.FileNameUtil
import java.io.File

// Files SQLite keeps next to a database. They belong to it and go with it.
private val COMPANION_FILE_SUFFIXES = listOf("-journal", "-wal", "-shm")

internal fun jdbcUrl(databaseFile: File): String = "jdbc:sqlite:${databaseFile.absolutePath}"

/**
 * The database file of [userId] inside [directory], named per user as on Apple.
 *
 * A fixed file name doesn't work: the backup export creates a second database for another user id
 * in the same folder, and deleting that one used to delete the user's own database.
 */
internal fun userDatabaseFile(directory: File, userId: UserIDEntity): File =
    directory.resolve(FileNameUtil.userDBName(userId))

/** Deletes [databaseFile] and the files SQLite keeps next to it. Returns true if none of them is left. */
internal fun deleteDatabaseFiles(databaseFile: File): Boolean =
    (listOf("") + COMPANION_FILE_SUFFIXES)
        .map { suffix -> File(databaseFile.path + suffix) }
        .map { file -> !file.exists() || file.delete() }
        .all { it }
