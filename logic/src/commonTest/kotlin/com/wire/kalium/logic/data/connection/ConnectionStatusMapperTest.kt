package com.wire.kalium.logic.data.connection

import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.network.api.user.connection.ConnectionStateDTO
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
