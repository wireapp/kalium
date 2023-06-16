/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
    const val BACKUP_FILE_NAME_PREFIX = "WBX"
    const val BACKUP_ENCRYPTED_FILE_NAME = "user-backup.cc20"
    const val BACKUP_USER_DB_NAME = "user-backup-database.db"
    const val BACKUP_METADATA_FILE_NAME = "export.json"
    const val BACKUP_ENCRYPTED_EXTENSION = "cc20"
    const val BACKUP_DB_EXTENSION = "db"
    const val BACKUP_METADATA_EXTENSION = "json"
    const val BACKUP_WEB_EXTENSION = "desktop_wbu"
    const val BACKUP_WEB_EVENTS_FILE_NAME = "events.json"
    const val BACKUP_WEB_CONVERSATIONS_FILE_NAME = "conversations.json"

    fun createBackupFileName(userHandle: String?, timestampIso: String) = // file names cannot have special characters
        "$BACKUP_FILE_NAME_PREFIX-$userHandle-${timestampIso.replace(":", "-")}.zip"

    val ACCEPTED_EXTENSIONS = listOf(
        BACKUP_ENCRYPTED_EXTENSION,
        BACKUP_DB_EXTENSION,
        BACKUP_METADATA_EXTENSION,
        BACKUP_WEB_EXTENSION
    )
}
