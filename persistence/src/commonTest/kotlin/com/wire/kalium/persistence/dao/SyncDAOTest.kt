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
package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SyncDAOTest: BaseDatabaseTest() {
    lateinit var userDAO: UserDAO
    lateinit var syncDAO: SyncDAO

    val SELF_USER_ID = UserIDEntity("self-id", "domain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(SELF_USER_ID)
        val db = createDatabase(SELF_USER_ID, encryptedDBSecret, true)
        userDAO = db.userDAO
        syncDAO = db.syncDAO
    }

    @Test
    fun givenUsersId_whenCallingAllOtherUsers_thenSelfIdIsNotIncluded() = runTest {
        val selfUser = newUserEntity().copy(id = SELF_USER_ID)
        val user1 = newUserEntity().copy(id = UserIDEntity("user-1", "domain-1"))
        val user2 = newUserEntity().copy(id = UserIDEntity("user-2", "domain-2"))
        val user3 = newUserEntity().copy(id = UserIDEntity("user-3", "domain-1"))

        userDAO.insertUser(selfUser)
        userDAO.insertUser(user1)
        userDAO.insertUser(user2)
        userDAO.insertUser(user3)

        syncDAO.allOtherUsersId().also { result ->
            assertFalse {
                result.contains(selfUser.id)
            }
            assertEquals(result, listOf(user1.id, user2.id, user3.id))
        }

    }
}
