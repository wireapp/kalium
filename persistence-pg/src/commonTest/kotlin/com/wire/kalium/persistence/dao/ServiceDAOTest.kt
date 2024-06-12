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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceDAOTest : BaseDatabaseTest() {

    private lateinit var serviceDAO: ServiceDAO
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        serviceDAO = db.serviceDAO
    }

    @Test
    fun givenNoServicesAreInserted_whenFetchingServiceById_thenReturnNull() = runTest {
        val result = serviceDAO.byId(id = serviceBotId)
        assertNull(result)
    }

    @Test
    fun givenInsertedService_whenFetchingServiceById_thenReturnService() = runTest {
        serviceDAO.insert(service = serviceEntity)

        val result = serviceDAO.byId(id = serviceBotId)
        assertEquals(serviceEntity.id, result?.id)
    }

    @Test
    fun givenServiceDoesNotExistsWhenSearchingByName_thenResultIsEmpty() = runTest {
        val result = serviceDAO.searchServicesByName(query = "non-existing")
        assertEquals(0, result.first().size)
    }

    @Test
    fun givenServiceExistsWhenSearchingByName_thenResultIsNotEmpty() = runTest {
        serviceDAO.insert(service = serviceEntity.copy(name = "existing"))

        val result = serviceDAO.searchServicesByName(query = "ex")
        assertEquals(1, result.first().size)
    }

    @Test
    fun givenNoServiceInserted_whenObservingAllServices_thenResultIsEmpty() = runTest {
        val result = serviceDAO.getAllServices()
        assertEquals(0, result.first().size)
    }

    @Test
    fun givenServiceInserted_whenObservingAllServices_thenResultIsNotEmpty() = runTest {
        serviceDAO.insert(service = serviceEntity)

        val result = serviceDAO.getAllServices()
        assertEquals(1, result.first().size)
    }

    private companion object {
        const val SERVICE_ID = "serviceId"
        const val PROVIDER_ID = "providerId"
        const val SERVICE_NAME = "Service Name"
        const val SERVICE_DESCRIPTION = "Service Description"
        const val SERVICE_SUMMARY = "Service Summary"
        const val SERVICE_ENABLED = true
        val SERVICE_TAGS = emptyList<String>()

        val serviceBotId = BotIdEntity(
            id = SERVICE_ID,
            provider = PROVIDER_ID
        )

        val serviceEntity = ServiceEntity(
            id = serviceBotId,
            name = SERVICE_NAME,
            description = SERVICE_DESCRIPTION,
            summary = SERVICE_SUMMARY,
            enabled = SERVICE_ENABLED,
            tags = SERVICE_TAGS,
            previewAssetId = null,
            completeAssetId = null
        )
    }
}
