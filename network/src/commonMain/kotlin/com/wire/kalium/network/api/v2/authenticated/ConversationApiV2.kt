package com.wire.kalium.network.api.v2.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponseDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationsDetailsRequest
import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.v0.authenticated.ConversationApiV0
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.post
import io.ktor.client.request.setBody

internal open class ConversationApiV2 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : ConversationApiV0(authenticatedNetworkClient) {
    override suspend fun fetchConversationsListDetails(
        conversationsIds: List<ConversationId>
    ): NetworkResponse<ConversationResponseDTO> =
        wrapKaliumResponse {
            httpClient.post("$PATH_CONVERSATIONS/$PATH_CONVERSATIONS_LIST") {
                setBody(ConversationsDetailsRequest(conversationsIds = conversationsIds))
            }
        }

}
