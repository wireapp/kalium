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

package com.wire.kalium.logic.data.connection

import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.network.api.authenticated.connection.ConnectionStateDTO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal typealias PersistenceConnectionState = com.wire.kalium.persistence.dao.ConnectionEntity.State

class ConnectionStatusMapperTest {

    private val mapper = ConnectionStatusMapperImpl()

    @Test
    fun givenApiModelConnectionState_whenMappingToDaoModel_thenTheFieldsShouldBeMappedCorrectly() {
        val accepted = ConnectionStateDTO.ACCEPTED
        val daoState = mapper.fromApiToDao(accepted)

        assertEquals(PersistenceConnectionState.ACCEPTED, daoState)
    }

    @Test
    fun givenApiModelConnectionState_whenMappingToModel_thenTheFieldsShouldBeMappedCorrectly() {
        val accepted = ConnectionStateDTO.ACCEPTED
        val daoState = mapper.fromApiModel(accepted)

        assertEquals(ConnectionState.ACCEPTED, daoState)
    }

    @Test
    fun givenAModelConnectionState_whenMappingToApiModel_thenTheFieldsShouldBeMappedCorrectly() {
        val accepted = ConnectionState.ACCEPTED
        val daoState = mapper.toApiModel(accepted)

        assertEquals(ConnectionStateDTO.ACCEPTED, daoState)
    }

    @Test
    fun givenDaoModelConnectionState_whenMappingToApiModel_thenTheFieldsShouldBeMappedCorrectly() {
        val accepted = PersistenceConnectionState.ACCEPTED
        val apiState = mapper.fromDaoToApi(accepted)

        assertEquals(ConnectionStateDTO.ACCEPTED, apiState)
    }

    @Test
    fun givenDaoModelConnectionState_whenMappingToApiModelToAnInvalid_thenTheFieldsShouldReturnsNull() {
        val accepted = PersistenceConnectionState.NOT_CONNECTED
        val apiState = mapper.fromDaoToApi(accepted)

        assertNull(apiState)
    }

    @Test
    fun givenAModelConnectionState_whenMappingToAnInvalidApiModel_thenTheReturnFieldShouldBeNull() {
        val accepted = ConnectionState.NOT_CONNECTED
        val apiState = mapper.toApiModel(accepted)

        assertNull(apiState)
    }
}
