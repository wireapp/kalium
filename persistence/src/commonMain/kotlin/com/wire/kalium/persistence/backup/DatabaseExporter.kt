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

package com.wire.kalium.persistence.backup

import app.cash.sqldelight.db.SqlDriver
import com.wire.kalium.persistence.DumpContentQueries
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.nuke
import com.wire.kalium.persistence.db.userDatabaseBuilder
import com.wire.kalium.persistence.kaliumLogger
import com.wire.kalium.util.KaliumDispatcherImpl

interface DatabaseExporter {

    /**
     * Export the user DB to a plain DB
     * @return the path to the plain DB file, null if the file was not created
     */
    fun exportToPlainDB(): String?

    /**
     * Delete the backup file and any temp data was created during the backup process
     * need to be called after the backup is done wether the user exported the file or not
     * even if the backup failed
     * @return true if the file was deleted, false otherwise
     */
    fun deleteBackupDBFile(): Boolean
}

internal class DatabaseExporterImpl internal constructor(
    user: UserIDEntity,
    private val platformDatabaseData: PlatformDatabaseData,
    private val dumpContentQueries: DumpContentQueries,
    private val sqlDriver: SqlDriver
) : DatabaseExporter {

    private val backupUserId = user.copy(value = "backup-${user.value}")

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    override fun exportToPlainDB(): String? {
        // delete the backup DB file if it exists
        if (deleteBackupDBFile()) {
            return null
        }

        // create a new backup DB file
        val plainDatabase: UserDatabaseBuilder =
            userDatabaseBuilder(platformDatabaseData, backupUserId, null, KaliumDispatcherImpl.io, false)
        plainDatabase.sqlDriver.close()

        // copy the data from the user DB to the backup DB

        try {
            sqlDriver.execute(null, "BEGIN", 0)
            sqlDriver.execute(null, "ATTACH DATABASE ? AS $PLAIN_DB_ALIAS", 1) {
                bindString(0, plainDatabase.dbFileLocation())
            }
            dumpContent()
            sqlDriver.execute(null, "COMMIT", 0)
        } catch (e: Exception) {
            kaliumLogger.e("Failed to dump the user DB to the plain DB ${e.stackTraceToString()}")
            // if the dump failed, delete the backup DB file
            deleteBackupDBFile()
            return null
        }
        return plainDatabase.dbFileLocation()
    }

    override fun deleteBackupDBFile(): Boolean = nuke(backupUserId, platformDatabaseData)

    private fun dumpContent() {
        with(dumpContentQueries) {
            // dump the content of the user DB into the plain DB must be done in this order
            dumpUserTable()
            dumpConversationTable()
            dumpMessageTable()
            dumpCallTable()
            dumpMessageAssetContentTable()
            dumpMessageRestrictedAssetContentTable()
            dumpMessageFailedToDecryptContentTable()
            dumpMessageConversationChangedContentTable()
            dumpMessageMemberChangeContentTable()
            dumpMessageMentionTable()
            dumpMessageMissedCallContentTable()
            dumpMessageTextContentTable()
            dumpMessageUnknownContentTable()
            dumpReactionTable()
            dumpRecieptTable()
        }
    }

    private companion object {
        // THIS MUST MATCH THE PLAIN DATABASE ALIAS IN DumpContent.sq DO NOT CHANGE
        const val PLAIN_DB_ALIAS = "plain_db"
    }
}
