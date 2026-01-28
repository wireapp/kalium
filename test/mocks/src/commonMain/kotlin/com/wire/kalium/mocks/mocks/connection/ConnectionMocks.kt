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
package com.wire.kalium.mocks.mocks.connection

import com.wire.kalium.network.api.authenticated.connection.ConnectionDTO
import com.wire.kalium.network.api.authenticated.connection.ConnectionResponse
import com.wire.kalium.network.api.authenticated.connection.ConnectionStateDTO
import com.wire.kalium.network.api.authenticated.connection.UpdateConnectionRequest
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.model.PaginationRequest
import com.wire.kalium.network.api.model.QualifiedID
import kotlinx.datetime.Instant

object ConnectionMocks {
    val connectionList = listOf(
        ConnectionDTO(
            conversationId = "addb6fbf-2bc3-4b59-b428-6fa4c594fb05",
            from = "36ef84a9-837a-4f75-af81-5a2e70e06836",
            lastUpdate = Instant.parse("2022-04-04T16:11:28.388Z"),
            qualifiedConversationId = ConversationId(
                domain = "staging.zinfra.io",
                value = "addb6fbf-2bc3-4b59-b428-6fa4c594fb05"
            ),
            qualifiedToId = QualifiedID(
                domain = "staging.zinfra.io",
                value = "76ebeb16-a849-4be4-84a7-157654b492cf"
            ),
            status = ConnectionStateDTO.ACCEPTED,
            toId = "76ebeb16-a849-4be4-84a7-157654b492cf"
        ),
        ConnectionDTO(
            conversationId = "af6d3c9a-7934-4790-9ebf-f655e13acc76",
            from = "36ef84a9-837a-4f75-af81-5a2e70e06836",
            lastUpdate = Instant.parse("2022-03-23T16:53:32.515Z"),
            qualifiedConversationId = ConversationId(
                domain = "staging.zinfra.io",
                value = "af6d3c9a-7934-4790-9ebf-f655e13acc76"
            ),
            qualifiedToId = QualifiedID(
                domain = "staging.zinfra.io",
                value = "787db7f1-f5ba-481b-af3e-9c27705a6440"
            ),
            status = ConnectionStateDTO.ACCEPTED,
            toId = "787db7f1-f5ba-481b-af3e-9c27705a6440"
        ),
        ConnectionDTO(
            conversationId = "f15a944a-b62b-4d9a-aff4-014d78a02294",
            from = "36ef84a9-837a-4f75-af81-5a2e70e06836",
            lastUpdate = Instant.parse("2022-03-25T17:20:13.637Z"),
            qualifiedConversationId = ConversationId(
                domain = "staging.zinfra.io",
                value = "f15a944a-b62b-4d9a-aff4-014d78a02294"
            ),
            qualifiedToId = QualifiedID(
                domain = "staging.zinfra.io",
                value = "ba6b0fa1-32b1-4e25-8072-a71f07bfba5e"
            ),
            status = ConnectionStateDTO.ACCEPTED,
            toId = "ba6b0fa1-32b1-4e25-8072-a71f07bfba5e"
        )
    )

    val connectionsResponse = ConnectionResponse(
        connections = connectionList,
        hasMore = false,
        pagingState = "AQ=="
    )

    val connection = ConnectionDTO(
        conversationId = "addb6fbf-2bc3-4b59-b428-6fa4c594fb05",
        from = "36ef84a9-837a-4f75-af81-5a2e70e06836",
        lastUpdate = Instant.parse("2022-04-04T16:11:28.388Z"),
        qualifiedConversationId = ConversationId(
            domain = "staging.zinfra.io",
            value = "addb6fbf-2bc3-4b59-b428-6fa4c594fb05"
        ),
        qualifiedToId = QualifiedID(
            domain = "staging.zinfra.io",
            value = "76ebeb16-a849-4be4-84a7-157654b492cf"
        ),
        status = ConnectionStateDTO.ACCEPTED,
        toId = "76ebeb16-a849-4be4-84a7-157654b492cf"
    )

    val emptyPaginationRequest = PaginationRequest(
        size = 500,
        pagingState = null
    )

    val paginationRequest = PaginationRequest(
        size = 500,
        pagingState = "PAGING_STATE_1234"
    )

    val acceptedConnectionRequest = UpdateConnectionRequest(ConnectionStateDTO.ACCEPTED)
}
