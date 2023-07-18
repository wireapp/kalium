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
package com.wire.kalium.persistence.dao.newclient

import app.cash.turbine.test
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.client.InsertClientParam
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NewClientDAOTest: BaseDatabaseTest() {

    private lateinit var newClientDAO: NewClientDAO
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        newClientDAO = db.newClientDAO
    }

    @Test
    fun whenANewClientsIsAdded_thenNewClientListIsEmitted() = runTest {
        newClientDAO.observeNewClients().test {
            awaitItem().also { result -> assertEquals(emptyList(), result) }
            newClientDAO.insertNewClient(insertedClient)

            awaitItem().also { result -> assertEquals(listOf(client), result) }
        }
    }

    @Test
    fun givenNewClients_whenClearNewClients_thenNewClientEmptyListIsEmitted() = runTest {
        newClientDAO.insertNewClient(insertedClient)
        newClientDAO.insertNewClient(insertedClient1)

        newClientDAO.observeNewClients().test {
            awaitItem()
            newClientDAO.clearNewClients()

            awaitItem().also { result -> assertEquals(listOf(), result) }
        }
    }

    private companion object {
        val userId = QualifiedIDEntity("test", "domain")
        val user = newUserEntity(userId)
        val insertedClient = InsertClientParam(
            userId = user.id,
            id = "id0",
            deviceType = null,
            clientType = null,
            label = null,
            model = null,
            registrationDate = null,
            lastActive = null
        )
        val insertedClient1 = insertedClient.copy(user.id, "id1", deviceType = null)

        val client = insertedClient.toClientEntity()
    }

}

private fun InsertClientParam.toClientEntity(): NewClientEntity =
    NewClientEntity(
        id,
        deviceType = deviceType,
        model = model,
        registrationDate = registrationDate
    )
