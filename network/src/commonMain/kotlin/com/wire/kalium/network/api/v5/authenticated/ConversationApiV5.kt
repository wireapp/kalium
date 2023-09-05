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

package com.wire.kalium.network.api.v5.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.conversation.SubconversationDeleteRequest
import com.wire.kalium.network.api.base.authenticated.conversation.SubconversationResponse
import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.base.model.QualifiedID
import com.wire.kalium.network.api.base.model.SubconversationId
import com.wire.kalium.network.api.v4.authenticated.ConversationApiV4
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.setBody

internal open class ConversationApiV5 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient,
) : ConversationApiV4(authenticatedNetworkClient) {

    override suspend fun fetchGroupInfo(conversationId: QualifiedID): NetworkResponse<ByteArray> =
        wrapKaliumResponse {
            httpClient.get(
                "$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_GROUP_INFO"
            )
        }

    override suspend fun fetchSubconversationGroupInfo(
        conversationId: ConversationId,
        subconversationId: SubconversationId
    ): NetworkResponse<ByteArray> =
        wrapKaliumResponse {
            httpClient.get(
                "$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/" +
                        "$PATH_SUBCONVERSATIONS/$subconversationId/$PATH_GROUP_INFO"
            )
        }

    override suspend fun fetchSubconversationDetails(
        conversationId: ConversationId,
        subconversationId: SubconversationId
    ): NetworkResponse<SubconversationResponse> =
        wrapKaliumResponse {
            httpClient.get(
                "$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}" +
                        "/$PATH_SUBCONVERSATIONS/$subconversationId"
            )
        }

    override suspend fun deleteSubconversation(
        conversationId: ConversationId,
        subconversationId: SubconversationId,
        deleteRequest: SubconversationDeleteRequest
    ): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.delete(
                "$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_SUBCONVERSATIONS/$subconversationId"
            ) {
                setBody(deleteRequest)
            }
        }

    override suspend fun leaveSubconversation(
        conversationId: ConversationId,
        subconversationId: SubconversationId
    ): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.delete(
                "$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_SUBCONVERSATIONS/$subconversationId/self"
            )
        }

}
