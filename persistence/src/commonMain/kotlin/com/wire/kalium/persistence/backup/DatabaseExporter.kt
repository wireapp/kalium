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

package com.wire.kalium.persistence.backup

import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.UserDBSecret
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.checkFKViolations
import com.wire.kalium.persistence.db.nuke
import com.wire.kalium.persistence.db.userDatabaseBuilder
import com.wire.kalium.persistence.kaliumLogger
import com.wire.kalium.util.KaliumDispatcherImpl
import io.mockative.Mockable

@Mockable
interface DatabaseExporter {

    /**
     * Export the user DB to a plain DB
     * @return the path to the plain DB file, null if the file was not created
     */
    fun exportToPlainDB(localDBPassphrase: UserDBSecret?): String?

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
    private val localDatabase: UserDatabaseBuilder
) : DatabaseExporter {

    private val backupUserId = user.copy(value = "backup-${user.value}")

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    override fun exportToPlainDB(localDBPassphrase: UserDBSecret?): String? {
        // delete the backup DB file if it exists
        if (!deleteBackupDBFile()) {
            kaliumLogger.e("Failed to delete the backup DB file")
            return null
        }

        // create a new backup DB file with empty passphrase
        // this will force the app to use sql Cipher and enable the encrypted local DB
        // to be attached to the plain DB
        // also unencrypted DB (in debug mode) can be attached to the plain DB
        // win win
        val plainDatabase: UserDatabaseBuilder =
            userDatabaseBuilder(
                platformDatabaseData,
                backupUserId,
                UserDBSecret(ByteArray(0)),
                KaliumDispatcherImpl.io,
                false
            )

        // check the plain DB path and return null if it was not created successfully
        plainDatabase.dbFileLocation().also {
            if (it == null) {
                kaliumLogger.e("Failed to get the plain DB path")
                return null
            }
        }

        // copy the data from the user DB to the backup DB
        if (!attachLocalToPlain(localDatabase, plainDatabase, localDBPassphrase)) {
            plainDatabase.sqlDriver.close()
            deleteBackupDBFile()
            return null
        }

        try {
            // attach the plain DB to the user DB
            // dump the content of the user DB into the plain DB
            plainDatabase.database.dumpContentQueries.dumpAllTables()
        } catch (e: Exception) {
            kaliumLogger.e("Failed to dump the user DB to the plain DB ${e.stackTraceToString()}")
            // if the dump failed, delete the backup DB file
            deleteBackupDBFile()
            return null
        } finally {
            // detach the plain DB from the user DB
            plainDatabase.sqlDriver.execute(null, "DETACH DATABASE $MAIN_DB_ALIAS", 0)
            if (plainDatabase.sqlDriver.checkFKViolations()) {
                kaliumLogger.e("Failed to dump the user DB to the plain DB, FK violations")
                plainDatabase.sqlDriver.close()
                // if the dump failed, delete the backup DB file
                deleteBackupDBFile()
                return null
            }
        }
        return plainDatabase.dbFileLocation()
    }

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    private fun attachLocalToPlain(
        localDatabase: UserDatabaseBuilder,
        plainDB: UserDatabaseBuilder,
        localDBPassphrase: UserDBSecret?
    ): Boolean {
        try {
            val mainDBPath = localDatabase.dbFileLocation() ?: return false

            if (localDBPassphrase == null) {
                plainDB.sqlDriver.execute(null, "ATTACH DATABASE ? AS $MAIN_DB_ALIAS", 1) {
                    bindString(0, mainDBPath)
                }
            } else {
                plainDB.sqlDriver.execute(null, "ATTACH DATABASE ? AS $MAIN_DB_ALIAS KEY ?", 2) {
                    bindString(0, mainDBPath)
                    bindBytes(1, localDBPassphrase.value)
                }
            }
        } catch (e: Exception) {
            kaliumLogger.e("Failed to attach the local DB to the plain DB ${e.message}")
            return false
        }
        return true
    }

    override fun deleteBackupDBFile(): Boolean = nuke(backupUserId, platformDatabaseData)

    private companion object {
        // THIS MUST MATCH THE PLAIN DATABASE ALIAS IN DumpContent.sq DO NOT CHANGE
        const val MAIN_DB_ALIAS = "local_db"
    }
}
