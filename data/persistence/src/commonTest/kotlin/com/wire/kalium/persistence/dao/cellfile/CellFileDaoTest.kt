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
package com.wire.kalium.persistence.dao.cellfile

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class CellFileDaoTest : BaseDatabaseTest() {

    private lateinit var database: UserDatabaseBuilder
    private lateinit var cellFileDao: CellFileDao
    private val selfUserId = UserIDEntity("selfId", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        database = createDatabase(selfUserId, encryptedDBSecret, true)
        cellFileDao = database.cellFileDao
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun givenOfflineFile_whenUpsertedWithIsOfflineFalse_thenOfflineStatusIsPreserved() = runTest(dispatcher) {
        val uuid = "test-asset-id"

        cellFileDao.upsert(
            CellFileEntity(
                uuid = uuid,
                conversationId = "conv@domain",
                name = "file.pdf",
                owner = null,
                localPath = "/data/file.pdf",
                size = 1024,
                downloadedAt = 1000L,
                isOffline = true,
            )
        )

        cellFileDao.upsert(
            CellFileEntity(
                uuid = uuid,
                conversationId = "conv@domain",
                name = "file.pdf",
                owner = null,
                localPath = "/data/file.pdf",
                size = 1024,
                downloadedAt = 2000L,
                isOffline = false,
            )
        )

        val result = cellFileDao.getById(uuid)
        assertEquals(result?.isOffline, true)
    }

    @Test
    fun givenLegacyOfflineFile_whenInserted_thenRemotePathIsNull() = runTest(dispatcher) {
        val file = offlineFile()
        cellFileDao.upsert(file)

        assertEquals(file, cellFileDao.getById(file.uuid))
        assertNull(database.database.cellFilesQueries.selectById(file.uuid).executeAsOne().assetPath)
    }

    @Test
    fun givenRemotePath_whenInserted_thenAllOfflineReadsReturnIt() = runTest(dispatcher) {
        val file = offlineFile("conv/reports/été #1.pdf")
        cellFileDao.upsert(file)

        assertEquals(file, cellFileDao.getById(file.uuid))
        assertEquals(listOf(file), cellFileDao.observeOfflineFiles().first())
        assertEquals(listOf(file), cellFileDao.observeOfflineFilesByConversationId(file.conversationId).first())
        assertEquals(file.remotePath, database.database.cellFilesQueries.selectById(file.uuid).executeAsOne().assetPath)
    }

    @Test
    fun givenRemotePath_whenUpdated_thenNewPathReplacesItAndNullPreservesIt() = runTest(dispatcher) {
        val file = offlineFile()
        cellFileDao.upsert(file)
        cellFileDao.upsert(file.copy(remotePath = "conv/old/file.pdf"))
        val moved = file.copy(remotePath = "conv/new/file.pdf", downloadedAt = 2000L)
        cellFileDao.upsert(moved)
        cellFileDao.upsert(moved.copy(remotePath = null, isOffline = false))

        assertEquals(moved, cellFileDao.getById(file.uuid))
        assertEquals(listOf(moved), cellFileDao.observeOfflineFiles().first())
    }

    @Test
    fun givenExistingAttachmentPath_whenSavedOfflineWithoutPath_thenAttachmentPathIsPreserved() = runTest(dispatcher) {
        // An attachment can already own assetPath before the offline save API is called.
        database.sqlDriver.execute(
            null,
            "INSERT INTO CellFile(uuid, downloadedAt, assetPath) VALUES ('file-id', 0, 'conv/reports/file.pdf')",
            0,
        )
        val file = offlineFile()
        cellFileDao.upsert(file)

        assertEquals(file.copy(remotePath = "conv/reports/file.pdf"), cellFileDao.getById(file.uuid))
    }

    @Test
    fun givenOfflinePaths_whenDatabaseReopenedAndOfflineAccessCleared_thenPathsRemainStored() = runTest(dispatcher) {
        val file = offlineFile("conv/reports/file.pdf")
        val legacy = offlineFile().copy(uuid = "legacy-file")
        cellFileDao.upsert(file)
        cellFileDao.upsert(legacy)
        database.close()
        database = createDatabase(selfUserId, encryptedDBSecret, true)
        cellFileDao = database.cellFileDao

        assertEquals(file, cellFileDao.getById(file.uuid))
        assertEquals(legacy, cellFileDao.getById(legacy.uuid))
        cellFileDao.clearOfflineAccess(file.uuid)
        val cleared = checkNotNull(cellFileDao.getById(file.uuid))
        assertFalse(cleared.isOffline)
        assertNull(cleared.localPath)
        assertEquals(file.remotePath, cleared.remotePath)
        assertEquals(listOf(legacy), cellFileDao.observeOfflineFiles().first())
    }

    private fun offlineFile(remotePath: String? = null) = CellFileEntity(
        uuid = "file-id",
        conversationId = "conv@domain",
        name = "file.pdf",
        owner = "owner",
        mimeType = "application/pdf",
        localPath = "/data/file.pdf",
        size = 1024,
        downloadedAt = 1000L,
        isOffline = true,
        modifiedAt = 500L,
        remotePath = remotePath,
    )

}
