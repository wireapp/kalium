package com.wire.kalium.logic.data.connection

import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.network.api.user.connection.ConnectionStateDTO
import com.wire.kalium.persistence.dao.UserEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConnectionStatusMapperTest {

    private val mapper = ConnectionStatusMapperImpl()

    @Test
    fun givenAModelConnectionState_whenMappingToDaoModel_thenTheFieldsShouldBeMappedCorrectly() {
        val accepted = ConnectionStateDTO.ACCEPTED
        val daoState = mapper.connectionStateToDao(accepted)

        assertEquals(UserEntity.ConnectionState.ACCEPTED, daoState)
    }

    @Test
    fun givenAModelConnectionState_whenMappingToApiModel_thenTheFieldsShouldBeMappedCorrectly() {
        val accepted = ConnectionState.ACCEPTED
        val apiState = mapper.connectionStateToApi(accepted)

        assertEquals(ConnectionStateDTO.ACCEPTED, apiState)
    }

    @Test
    fun givenAModelConnectionState_whenMappingToApiModelToAnInvalid_thenTheFieldsShouldReturnsNull() {
        val accepted = ConnectionState.NOT_CONNECTED
        val apiState = mapper.connectionStateToApi(accepted)

        assertNull(apiState)
    }
}
