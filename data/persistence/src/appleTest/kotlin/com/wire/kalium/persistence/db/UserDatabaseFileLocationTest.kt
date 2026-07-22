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

package com.wire.kalium.persistence.db

import app.cash.sqldelight.async.coroutines.synchronous
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.util.FileNameUtil
import platform.Foundation.NSURL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression: apple `getDatabaseAbsoluteFileLocation` used to return the
 * storage directory instead of the DB file path, so backup export failed with
 * `ATTACH DATABASE '<dir>'` and surfaced as `Backup failed: DataNotFound`.
 */
class UserDatabaseFileLocationTest : BaseDatabaseTest() {

    private val userId = UserIDEntity("file-location-test", "wire.local")
    private lateinit var builder: UserDatabaseBuilder

    @BeforeTest
    fun setUp() {
        deleteDatabase(userId)
        builder = createDatabase(userId, passphrase = null, enableWAL = false)
    }

    @AfterTest
    fun tearDown() {
        builder.sqlDriver.close()
        deleteDatabase(userId)
    }

    @Test
    fun givenFileBackedUserDatabase_whenAskingForDbFileLocation_thenReturnsAnExistingFilePathEndingInUserDbName() {
        val location = builder.dbFileLocation()

        assertNotNull(location, "dbFileLocation() must not be null for a file-backed DB")
        assertTrue(
            location.endsWith(FileNameUtil.userDBName(userId)),
            "Expected path to end with '${FileNameUtil.userDBName(userId)}' but was '$location'"
        )
        assertTrue(
            NSURL.fileURLWithPath(location).checkResourceIsReachableAndReturnError(null),
            "DB file at '$location' does not exist on disk"
        )
    }

    @Test
    fun givenFileBackedUserDatabase_whenAttachingDbFileLocation_thenAttachSucceeds() {
        val location = assertNotNull(builder.dbFileLocation())

        // A second driver must be able to ATTACH the reported path. This is
        // exactly what the backup export path does; it used to fail because
        // the returned path pointed at the containing directory.
        val attachDriver = databaseDriver(
            driverUri = null,
            dbName = "attach-check-${userId.value}",
            schema = UserDatabase.Schema.synchronous()
        ) {
            isWALEnabled = false
        }
        try {
            attachDriver.execute(null, "ATTACH DATABASE ? AS attached_local", 1) {
                bindString(0, location)
            }
            attachDriver.execute(null, "DETACH DATABASE attached_local", 0)
        } finally {
            attachDriver.close()
        }
    }
}
