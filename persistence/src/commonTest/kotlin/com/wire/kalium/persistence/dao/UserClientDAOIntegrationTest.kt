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

package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.client.Client
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.client.InsertClientParam
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertTrue

class UserClientDAOIntegrationTest : BaseDatabaseTest() {

    private lateinit var clientDAO: ClientDAO
    private lateinit var userDAO: UserDAO
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        clientDAO = db.clientDAO
        userDAO = db.userDAO
    }

    @Test
    fun givenClientsAreInserted_whenDeletingTheUser_thenTheClientsAreDeleted() = runTest {
        userDAO.upsertUser(user)
        clientDAO.insertClient(insertClientParam)

        userDAO.deleteUserByQualifiedID(user.id)

        val result = clientDAO.getClientsOfUserByQualifiedIDFlow(user.id).first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun givenUserIsNotInserted_whenInsertingClient_thenAnExceptionIsThrown() = runTest {
        // Exception depends on each platform/sqlite driver, can't assert exception type or message in common source
        assertFails {
            clientDAO.insertClient(insertClientParam)
        }
    }

    private companion object {
        val userId = QualifiedIDEntity("test", "domain")
        val user = newUserEntity(qualifiedID = userId)
        val client = Client(
            userId = user.id,
            id = "id1",
            deviceType = null,
            isValid = true,
            isProteusVerified = false,
            registrationDate = null,
            lastActive = null,
            label = null,
            clientType = null,
            model = null,
            mlsPublicKeys = null,
            isMLSCapable = false
        )
        val insertClientParam = InsertClientParam(
            client.userId,
            client.id,
            client.deviceType,
            client.clientType,
            client.label,
            client.registrationDate,
            client.lastActive,
            client.model,
            client.mlsPublicKeys,
            client.isMLSCapable
        )
    }
}
