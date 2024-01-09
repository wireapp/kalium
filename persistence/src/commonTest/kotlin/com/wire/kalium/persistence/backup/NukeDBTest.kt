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

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.nuke
import com.wire.kalium.persistence.utils.IgnoreIOS
import com.wire.kalium.persistence.utils.IgnoreJvm
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@IgnoreIOS
@IgnoreJvm
class NukeDBTest : BaseDatabaseTest() {
    private lateinit var localDB: UserDatabaseBuilder

    private val selfUserId = UserIDEntity("selfValue", "selfDomain")
    private val backupUserId = UserIDEntity("backup-selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        localDB = createDatabase(selfUserId, passphrase = null, enableWAL = false)
    }

    @Test
    fun givenDB_whenDeleted_thenItIsDeleted() {
        assertTrue { nuke(selfUserId, platformDatabaseData = platformDBData(selfUserId)) }
        assertFalse { doesDatabaseExist(selfUserId) }
    }

    @Test
    fun givenDBNotFound_whenNuke_thenReturnTrue() {
        assertTrue { nuke(selfUserId.copy(value = "some id"), platformDatabaseData = platformDBData(selfUserId)) }
    }
}
