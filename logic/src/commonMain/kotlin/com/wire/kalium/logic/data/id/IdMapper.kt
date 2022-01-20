package com.wire.kalium.logic.data.id

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.network.api.user.client.SimpleClientResponse

internal typealias NetworkQualifiedId = com.wire.kalium.network.api.QualifiedID

interface IdMapper {
    fun fromApiModel(networkId: NetworkQualifiedId): QualifiedID
    fun fromSimpleClientResponse(clientResponse: SimpleClientResponse): ClientId
    fun toApiModel(qualifiedID: QualifiedID): NetworkQualifiedId
}

internal class IdMapperImpl : IdMapper {

    override fun fromApiModel(networkId: NetworkQualifiedId) = QualifiedID(value = networkId.value, domain = networkId.domain)

    override fun fromSimpleClientResponse(clientResponse: SimpleClientResponse) = ClientId(clientResponse.id)

    override fun toApiModel(qualifiedID: QualifiedID) = NetworkQualifiedId(value = qualifiedID.value, domain = qualifiedID.domain)
}
