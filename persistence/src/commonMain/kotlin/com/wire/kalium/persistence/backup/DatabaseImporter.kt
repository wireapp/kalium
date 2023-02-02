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

import com.wire.kalium.persistence.ImportContentQueries
import com.wire.kalium.persistence.db.UserDatabaseBuilder

interface DatabaseImporter {
    suspend fun importFromFile(filePath: String, fromOtherClient: Boolean)
}

internal class DatabaseImporterImpl internal constructor(
    private val localDatabase: UserDatabaseBuilder,
    private val importContentQueries: ImportContentQueries,
    private val isDataEncrypted: Boolean
) : DatabaseImporter {
    private val localDBDriver = localDatabase.sqlDriver

    override suspend fun importFromFile(filePath: String, fromOtherClient: Boolean) {

        localDatabase.database.transaction {
            attachBackupDB(filePath)

            with(importContentQueries) {
                importUserTable()
                importConversationTable()
                importMessageTable()
                if (!fromOtherClient) importCallsTable()
                importMessageAssetContentTable()
                importMessageRestrictedAssetContentTable()
                importMessageConversationChangedContentTable()
                importMessageFailedToDecryptContentTable()
                importMessageMemberChangeContentTable()
                importMessageMentionTable()
                importMessageMissedCallContentTable()
                importMessageTextContentTable()
                importMessageUnknownContentTable()
                importReactionTable()
                importRecieptTable()
            }
        }
    }

    private fun attachBackupDB(filePath: String) {
        if (isDataEncrypted) {
            localDBDriver.execute(null, """ATTACH ? AS $BACKUP_DB_ALIAS KEY ''""", 1) {
                bindString(0, filePath)
            }
        } else {
            localDBDriver.execute(null, """ATTACH ? AS $BACKUP_DB_ALIAS""", 1) {
                bindString(0, filePath)
            }
        }
    }

    private companion object {
        // this key must be the same as the one used in ImportContentQueries.kt carefuller when changing it
        const val BACKUP_DB_ALIAS = "backup_db"
    }
}
