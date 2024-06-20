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

import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.authenticated.connection.ConnectionDTO
import com.wire.kalium.network.api.authenticated.connection.ConnectionStateDTO
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

        val mapper = ConnectionMapperImpl(idMapper, statusMapper)

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
