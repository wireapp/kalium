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

import com.wire.backup.data.BackupConversation
import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.data.BackupUser
import com.wire.backup.ingest.BackupImportResult

abstract class BackupSamples {
    fun getSelfUserId(): BackupQualifiedId = TODO()
    fun getMessagesFromDatabase(): List<BackupMessage> = TODO()
    fun getUsersFromDatabase(): List<BackupUser> = TODO()
    fun getConversationsFromDatabase(): List<BackupConversation> = TODO()
    fun updateProgress(totalPages: Int, currentPage: Int): Unit = TODO()

    fun commonImport(importResult: BackupImportResult) {
        // Handling import result
        when (importResult) {
            BackupImportResult.Failure.MissingOrWrongPassphrase -> TODO("User has provided a wrong password")
            BackupImportResult.Failure.ParsingFailure -> TODO("This file is not a valid backup file, or its an unsupported version")
            is BackupImportResult.Failure.UnknownError -> TODO("Exception. You can get more info by doing ${importResult.message}")
            is BackupImportResult.Failure.UnzippingError -> TODO("Client implementation of Unzipper has thrown an exception")

            is BackupImportResult.Success -> {
                val importPager = importResult.pager
                // You can calculate progress based on total page count:
                val totalPages = importPager.totalPagesCount
                var processedPages = 0

                while (importPager.conversationsPager.hasMorePages()) {
                    updateProgress(totalPages, ++processedPages)
                    val conversations = importPager.conversationsPager.nextPage()
                    conversations.forEach { conversation ->
                        TODO("Map each conversation and insert into local Database")
                    }
                }

                while (importPager.usersPager.hasMorePages()) {
                    updateProgress(totalPages, ++processedPages)
                    val users = importPager.usersPager.nextPage()
                    users.forEach { user ->
                        TODO("Map each user and insert into local Database")
                    }
                }

                while (importPager.messagesPager.hasMorePages()) {
                    updateProgress(totalPages, ++processedPages)
                    val messages = importPager.messagesPager.nextPage()
                    messages.forEach { message ->
                        TODO("Map each message and insert into local Database")
                    }
                }

                println("Import finished successfully!")
            }
        }
    }
}
