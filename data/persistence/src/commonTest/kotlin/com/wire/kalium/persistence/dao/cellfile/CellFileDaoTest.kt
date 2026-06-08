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
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CellFileDaoTest : BaseDatabaseTest() {

    private lateinit var cellFileDao: CellFileDao
    private val selfUserId = UserIDEntity("selfId", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        cellFileDao = db.cellFileDao
    }

    @Test
    fun givenOfflineFile_whenUpsertedWithIsOfflineFalse_thenOfflineStatusIsPreserved() = runTest {
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
}
