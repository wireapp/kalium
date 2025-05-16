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

object BackupConstants {
    const val BACKUP_FILE_NAME_PREFIX = "Wire"
    const val BACKUP_ENCRYPTED_FILE_NAME = "user-backup.cc20"

    // BACKUP_METADATA_FILE_NAME and BACKUP_USER_DB_NAME must not be changed
    // if there is a need to change them, please create a new file names and add it to the list of acceptedFileNames()
    const val BACKUP_USER_DB_NAME = "user-backup-database.db"
    const val BACKUP_METADATA_FILE_NAME = "export.json"
    const val BACKUP_ENCRYPTED_EXTENSION = "cc20"
    const val BACKUP_DB_EXTENSION = "db"
    const val BACKUP_METADATA_EXTENSION = "json"
    const val BACKUP_WEB_EXTENSION = "desktop_wbu"
    const val BACKUP_WEB_EVENTS_FILE_NAME = "events.json"
    const val BACKUP_WEB_CONVERSATIONS_FILE_NAME = "conversations.json"

    /**
     * list of accepted file names for the backup file
     * this is used when extracting data from the zip file
     */
    fun acceptedFileNames() = setOf(
        BACKUP_USER_DB_NAME,
        BACKUP_METADATA_FILE_NAME,
        BACKUP_ENCRYPTED_FILE_NAME,
        BACKUP_WEB_EVENTS_FILE_NAME,
        BACKUP_WEB_CONVERSATIONS_FILE_NAME
    )

    fun createBackupFileName(userHandle: String?, timestampIso: String) = // file names cannot have special characters
        "$BACKUP_FILE_NAME_PREFIX-$userHandle-${timestampIso.replace(":", "-")}.wbu"

    val ACCEPTED_EXTENSIONS = listOf(
        BACKUP_ENCRYPTED_EXTENSION,
        BACKUP_DB_EXTENSION,
        BACKUP_METADATA_EXTENSION,
        BACKUP_WEB_EXTENSION
    )
}
