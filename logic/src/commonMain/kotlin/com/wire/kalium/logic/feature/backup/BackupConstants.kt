/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.backup

internal object BackupConstants {
    internal const val BACKUP_FILE_NAME_PREFIX = "Wire"
    internal const val BACKUP_ENCRYPTED_FILE_NAME = "user-backup.cc20"

    // BACKUP_METADATA_FILE_NAME and BACKUP_USER_DB_NAME must not be changed
    // if there is a need to change them, please create a new file names and add it to the list of acceptedFileNames()
    internal const val BACKUP_USER_DB_NAME = "user-backup-database.db"
    internal const val BACKUP_METADATA_FILE_NAME = "export.json"
    internal const val BACKUP_ENCRYPTED_EXTENSION = "cc20"
    internal const val BACKUP_DB_EXTENSION = "db"
    internal const val BACKUP_METADATA_EXTENSION = "json"
    internal const val BACKUP_WEB_EXTENSION = "desktop_wbu"
    internal const val BACKUP_WEB_EVENTS_FILE_NAME = "events.json"
    internal const val BACKUP_WEB_CONVERSATIONS_FILE_NAME = "conversations.json"

    /**
     * list of accepted file names for the backup file
     * this is used when extracting data from the zip file
     */
    internal fun acceptedFileNames() = setOf(
        BACKUP_USER_DB_NAME,
        BACKUP_METADATA_FILE_NAME,
        BACKUP_ENCRYPTED_FILE_NAME,
        BACKUP_WEB_EVENTS_FILE_NAME,
        BACKUP_WEB_CONVERSATIONS_FILE_NAME
    )

    internal fun createBackupFileName(userHandle: String?, timestampIso: String, multiplatform: Boolean = true): String {
        val extension = if (multiplatform) "wbu" else "zip"
        return "$BACKUP_FILE_NAME_PREFIX-$userHandle-${timestampIso.replace(":", "-")}.$extension"
    }

    internal val ACCEPTED_EXTENSIONS = listOf(
        BACKUP_ENCRYPTED_EXTENSION,
        BACKUP_DB_EXTENSION,
        BACKUP_METADATA_EXTENSION,
        BACKUP_WEB_EXTENSION
    )
}
