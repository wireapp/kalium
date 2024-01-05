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
package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecipientDeliveryFailureMapperTest {
    @Test
    fun givenNoDeliveryErrors_whenMappingFromDB_shouldReturnNoDeliveryError() = runTest {
        val result = RecipientDeliveryFailureMapper.toEntity(listOf(), listOf())

        assertEquals(
            DeliveryStatusEntity.CompleteDelivery,
            result
        )
    }

    @Test
    fun givenThereAreErrors_whenMappingFromDB_shouldReturnPartialDeliveryErrorWithUsersIds() = runTest {
        val result =
            RecipientDeliveryFailureMapper.toEntity(
                recipientsFailedWithNoClientsList = listOf(UserIDEntity("user1", "domain1.com")),
                recipientsFailedDeliveryList = listOf(UserIDEntity("user2", "domain1.com"))
            )

        assertTrue(result is DeliveryStatusEntity.PartialDelivery)
        assertTrue(result.recipientsFailedWithNoClients.isNotEmpty() && result.recipientsFailedDelivery.isNotEmpty())
        assertEquals(result.recipientsFailedDelivery.first(), UserIDEntity("user2", "domain1.com"))
        assertEquals(result.recipientsFailedWithNoClients.first(), UserIDEntity("user1", "domain1.com"))
    }
}

