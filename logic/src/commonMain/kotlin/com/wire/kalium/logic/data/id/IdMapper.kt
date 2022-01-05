package com.wire.kalium.logic.data.id

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.network.api.user.client.SimpleClientResponse

internal typealias NetworkQualifiedId = com.wire.kalium.network.api.QualifiedID

class IdMapper {

    fun fromApiModel(networkId: NetworkQualifiedId) = QualifiedID(value = networkId.value, domain = networkId.domain)

    fun fromSimpleClientResponse(clientResponse: SimpleClientResponse) = ClientId(clientResponse.id)

    fun toApiModel(qualifiedID: QualifiedID) = NetworkQualifiedId(value = qualifiedID.value, domain = qualifiedID.domain)
}
