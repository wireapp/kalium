package com.wire.kalium.logic.data.id

import com.wire.kalium.cryptography.CryptoQualifiedID
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.user.client.SimpleClientResponse

internal typealias NetworkQualifiedId = com.wire.kalium.network.api.QualifiedID
internal typealias PersistenceQualifiedId = com.wire.kalium.persistence.dao.QualifiedIDEntity

interface IdMapper {
    fun fromApiModel(networkId: NetworkQualifiedId): QualifiedID
    fun fromSimpleClientResponse(clientResponse: SimpleClientResponse): ClientId
    fun fromDaoModel(persistenceId: PersistenceQualifiedId): QualifiedID
    fun toApiModel(qualifiedID: QualifiedID): NetworkQualifiedId
    fun toDaoModel(qualifiedID: QualifiedID): PersistenceQualifiedId
    fun fromApiToDao(qualifiedID: NetworkQualifiedId): PersistenceQualifiedId
    fun toCryptoQualifiedIDId(qualifiedID: QualifiedID): CryptoQualifiedID
}

internal class IdMapperImpl : IdMapper {

    override fun fromApiModel(networkId: NetworkQualifiedId) = QualifiedID(value = networkId.value, domain = networkId.domain)

    override fun fromSimpleClientResponse(clientResponse: SimpleClientResponse) = ClientId(clientResponse.id)

    override fun fromDaoModel(persistenceId: PersistenceQualifiedId) = QualifiedID(value = persistenceId.value, domain = persistenceId.domain)

    override fun toApiModel(qualifiedID: QualifiedID) = NetworkQualifiedId(value = qualifiedID.value, domain = qualifiedID.domain)

    override fun toDaoModel(qualifiedID: QualifiedID): PersistenceQualifiedId = PersistenceQualifiedId(value = qualifiedID.value, domain = qualifiedID.domain)

    override fun fromApiToDao(qualifiedID: NetworkQualifiedId) = PersistenceQualifiedId(value = qualifiedID.value, domain = qualifiedID.domain)

    override fun toCryptoQualifiedIDId(qualifiedID: QualifiedID): CryptoQualifiedID = CryptoQualifiedID(value = qualifiedID.value, domain = qualifiedID.domain)
    
}
