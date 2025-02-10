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
package com.wire.kalium.persistence.backup

import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.UserDBSecret
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.createEmptyDatabaseFile
import com.wire.kalium.persistence.db.nuke
import com.wire.kalium.persistence.db.userDatabaseDriverByPath
import com.wire.kalium.persistence.kaliumLogger
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.FileUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

@DelicateKaliumApi("This class is used for debugging purposes only")
class ObfuscatedCopyExporter internal constructor(
    user: UserIDEntity,
    private val platformDatabaseData: PlatformDatabaseData,
    private val localDatabase: UserDatabaseBuilder,
    private val kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) {
    private val emptyPlainFIleNameUserId = user.copy(value = ("obfuscated-backup-" + user.value))

    fun deleteCopyFile() {
        nuke(emptyPlainFIleNameUserId, platformDatabaseData)
    }

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    suspend fun exportToPlainDB(): String? {

        val emptyPlainFilePath: String = createEmptyDatabaseFile(platformDatabaseData, emptyPlainFIleNameUserId) ?: return null

        attachAndExport(localDatabase, emptyPlainFilePath)
        val obfuscateResult = obfuscatePlainCopy(emptyPlainFilePath)

        return if (obfuscateResult) {
            emptyPlainFilePath
        } else {
            FileUtil.deleteDirectory(emptyPlainFilePath)
            null
        }
    }

    private suspend fun attachAndExport(localDatabase: UserDatabaseBuilder, emptyPlainFilePath: String) = withContext(kaliumDispatcher.io) {
        localDatabase.sqlDriver.execute(
            null,
            "ATTACH DATABASE ? AS $OBFUSCATED_PLAIN_DB KEY ''",
            0
        ) {
            bindString(0, emptyPlainFilePath)
        }

        localDatabase.sqlDriver.executeQuery(
            null,
            "SELECT sqlcipher_export('$OBFUSCATED_PLAIN_DB')",
            { cursor -> cursor.next() },
            0
        )

        localDatabase.sqlDriver.execute(
            null,
            "DETACH DATABASE $OBFUSCATED_PLAIN_DB",
            0
        )
    }

    @Suppress("TooGenericExceptionCaught", "LongMethod")
    private suspend fun obfuscatePlainCopy(
        plainDBPath: String,
    ): Boolean = withContext(kaliumDispatcher.io) {
        val plainDBDriver = userDatabaseDriverByPath(
            platformDatabaseData,
            plainDBPath,
            UserDBSecret(ByteArray(0)),
            false
        )

        try {
            plainDBDriver.execute(
                null,
                "UPDATE MessageTextContent " +
                        "SET text_body = CASE " +
                        "    WHEN text_body IS NOT NULL " +
                        "    THEN substr(hex(randomblob(length(text_body))), 1, length(text_body)) " +
                        "    ELSE NULL " +
                        "END " +
                        "WHERE text_body IS NOT NULL;",
                0
            )

            plainDBDriver.execute(
                null,
                "UPDATE MessageLinkPreview " +
                        "SET " +
                        "  url = CASE " +
                        "          WHEN url IS NOT NULL " +
                        "          THEN substr(hex(randomblob(length(url))), 1, length(url)) " +
                        "          ELSE NULL " +
                        "        END, " +
                        "  permanent_url = CASE " +
                        "                    WHEN permanent_url IS NOT NULL " +
                        "                    THEN substr(hex(randomblob(length(permanent_url))), 1, length(permanent_url)) " +
                        "                    ELSE NULL " +
                        "                  END, " +
                        "  title = CASE " +
                        "            WHEN title IS NOT NULL " +
                        "            THEN substr(hex(randomblob(length(title))), 1, length(title)) " +
                        "            ELSE NULL " +
                        "          END, " +
                        "  summary = CASE " +
                        "              WHEN summary IS NOT NULL " +
                        "              THEN substr(hex(randomblob(length(summary))), 1, length(summary)) " +
                        "              ELSE NULL " +
                        "            END " +
                        "WHERE url IS NOT NULL " +
                        "   OR permanent_url IS NOT NULL " +
                        "   OR title IS NOT NULL " +
                        "   OR summary IS NOT NULL;",
                0
            )

            plainDBDriver.execute(
                null,
                "UPDATE MessageAssetContent " +
                        "SET " +
                        "  asset_sha256 = CASE " +
                        "                   WHEN asset_sha256 IS NOT NULL " +
                        "                   THEN randomblob(length(asset_sha256)) " +
                        "                   ELSE NULL " +
                        "                 END " +
                        "WHERE asset_otr_key IS NOT NULL " +
                        "   OR asset_sha256 IS NOT NULL;",
                0
            )

            plainDBDriver.execute(
                null,
                "UPDATE MessageDraft " +
                        "SET " +
                        "  text = CASE " +
                        "                   WHEN text IS NOT NULL " +
                        "                   THEN randomblob(length(text)) " +
                        "                   ELSE NULL " +
                        "                 END " +
                        "WHERE text IS NOT NULL;",
                0
            )
        } catch (e: Exception) {
            kaliumLogger.e("Failed to attach the local DB to the plain DB: ${e.stackTraceToString()}")
            return@withContext false
        } finally {
            plainDBDriver.close()
        }
        return@withContext true
    }

    private companion object {
        // THIS MUST MATCH THE PLAIN DATABASE ALIAS IN DumpContent.sq DO NOT CHANGE
        const val OBFUSCATED_PLAIN_DB = "obfuscated_plain_db"
    }
}
