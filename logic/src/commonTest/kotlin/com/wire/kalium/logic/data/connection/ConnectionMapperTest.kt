package com.wire.kalium.logic.data.connection

import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.api.base.authenticated.connection.ConnectionDTO
import com.wire.kalium.network.api.base.authenticated.connection.ConnectionStateDTO
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import kotlinx.datetime.Instant
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
        val connectionEntity = mapper.fromDaoToModel(arrangement.stubConnectionEntity)

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
            UNIX_FIRST_DATE,
            ConversationId("someId", "someDomain"),
            UserId("someId", "someDomain"),
            ConnectionStateDTO.ACCEPTED,
            "toId"
        )

        val stubConnection = Connection(
            "someId",
            "from",
            UNIX_FIRST_DATE,
            ModelConversationId("someId", "someDomain"),
            ModelConversationId("someId", "someDomain"),
            ConnectionState.ACCEPTED,
            "toId",
            null
        )

        val stubConnectionEntity = ConnectionEntity(
            "someId",
            "from",
            Instant.UNIX_FIRST_DATE,
            ConversationIDEntity("someId", "someDomain"),
            QualifiedIDEntity("someId", "someDomain"),
            ConnectionEntity.State.ACCEPTED,
            "toId"
        )

        fun arrange() = this to mapper
    }
}
