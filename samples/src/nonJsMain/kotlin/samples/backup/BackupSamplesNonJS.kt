/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package samples.backup

import com.wire.backup.dump.MPBackupExporter
import com.wire.backup.ingest.BackupPeekResult
import com.wire.backup.ingest.MPBackupImporter
import com.wire.backup.ingest.isCreatedBySameUser

object BackupSamplesNonJS : BackupSamples() {

    suspend fun exportBackup() {
        val backupPassword = "Aqa123456!"
        // Create a MPBackupExporter
        val mpBackupExporter = MPBackupExporter(
            selfUserId = getSelfUserId(),
            workDirectory = "/path/to/working/directory/",
            outputDirectory = "/path/to/output/directory/",
            fileZipper = { entries, path ->
                TODO("Zip all entries into a single zip file and return the path to this file")
            }
        )

        // Each client (iOS, Web, Android) has their own logic for fetching stuff from database
        // It's probably necessary to paginate the data from database, in case there are tens of thousands of messages, for example
        getMessagesFromDatabase().forEach { message ->
            mpBackupExporter.add(message)
        }

        getUsersFromDatabase().forEach { user ->
            mpBackupExporter.add(user)
        }

        getConversationsFromDatabase().forEach { conversation ->
            mpBackupExporter.add(conversation)
        }

        // When all data is exported, you can call finalize, saving the result into a file.
        val pathToBackupFile = mpBackupExporter.finalize(backupPassword)
        println("Backup created at $pathToBackupFile")
    }

    suspend fun peekBackup() {
        // Peek into backup file

        val importer = MPBackupImporter(
            pathToWorkDirectory = "/path/to/working/directory",
            backupFileUnzipper = { zippedFile ->
                TODO("Unzip all content into a directory, and return the path to the unzipped content")
            }
        )

        // Check if the backup is encrypted or not
        val peekResult = importer.peekBackupFile("path/to/backupFile.wbu")
        when (peekResult) {
            BackupPeekResult.Failure.UnknownFormat -> TODO("This is not a valid backup file")
            is BackupPeekResult.Failure.UnsupportedVersion -> TODO("Unsupported version, too old or too new")
            is BackupPeekResult.Success -> {
                // You can check if the backup was created by a specific user ID:
                val isCreatedBySameUser = peekResult.isCreatedBySameUser(getSelfUserId())
                println("Backup info: isEncrypted=${peekResult.isEncrypted}, version=${peekResult.version}")
            }
        }
    }

}
