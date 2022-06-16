package com.wire.kalium.logic.data.connection

import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.user.connection.ConnectionDTO
import com.wire.kalium.network.api.user.connection.ConnectionStateDTO
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import com.wire.kalium.logic.data.id.ConversationId as ModelConversationId


class ConnectionMapperTest {
    @Test
    fun givenAConnectionResponse_whenMappingFromConnectionResponse_thenTheStatusShouldBeCorrect() {
        // given
        val (arrangement, mapper) = Arrangement().arrange()

        // when
        val connectionModel = mapper.fromApiToModel(arrangement.stubConnectionResponse)

        // then
        assertEquals(ConnectionState.ACCEPTED, connectionModel.status)
    }

    @Test
    fun givenAConnectionModel_whenMappingToDao_thenTheStatusShouldBeCorrect() {
        // given
        val (arrangement, mapper) = Arrangement().arrange()

        // when
        val connectionDao = mapper.modelToDao(arrangement.stubConnection)

        // then
        assertEquals(ConnectionEntity.State.ACCEPTED, connectionDao.status)
    }


    @Test
    fun givenAConnectionEntity_whenMappingToModel_thenTheStatusShouldBeCorrect() {
        // given
        val (arrangement, mapper) = Arrangement().arrange()

        // when
        val connectionEntity = mapper.fromDaoToModel(arrangement.stubConnectionEntity, null)

        // then
        assertEquals(ConnectionState.ACCEPTED, connectionEntity.status)
    }

    @Test
    fun givenAConnectionResponse_whenMappingToDao_thenTheStatusShouldBeCorrect() {
        // given
        val (arrangement, mapper) = Arrangement().arrange()

        // when
        val connectionEntity = mapper.fromApiToDao(arrangement.stubConnectionResponse)

        // then
        assertEquals(ConnectionEntity.State.ACCEPTED, connectionEntity.status)
    }


    private class Arrangement {
        val idMapper = MapperProvider.idMapper()

        val statusMapper = MapperProvider.connectionStatusMapper()

        val publicUserMapper = MapperProvider.publicUserMapper()

        val mapper = ConnectionMapperImpl(idMapper, statusMapper, publicUserMapper)

        val stubConnectionResponse = ConnectionDTO(
            "someId",
            "from",
            "lastUpdate",
            ConversationId("someId", "someDomain"),
            UserId("someId", "someDomain"),
            ConnectionStateDTO.ACCEPTED,
            "toId"
        )

        val stubConnection = Connection(
            "someId",
            "from",
            "lastUpdate",
            ModelConversationId("someId", "someDomain"),
            ModelConversationId("someId", "someDomain"),
            ConnectionState.ACCEPTED,
            "toId",
            null
        )

        val stubConnectionEntity = ConnectionEntity(
            "someId",
            "from",
            "lastUpdate",
            ConversationIDEntity("someId", "someDomain"),
            QualifiedIDEntity("someId", "someDomain"),
            ConnectionEntity.State.ACCEPTED,
            "toId"
        )

        fun arrange() = this to mapper
    }
}
