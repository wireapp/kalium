package com.wire.kalium.logic.data.id

import com.wire.kalium.cryptography.CryptoQualifiedID
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.network.api.user.client.SimpleClientResponse
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.protobuf.messages.QualifiedConversationId

internal typealias NetworkQualifiedId = com.wire.kalium.network.api.QualifiedID
internal typealias PersistenceQualifiedId = QualifiedIDEntity

@Suppress("TooManyFunctions")
interface IdMapper {
    fun fromApiModel(networkId: NetworkQualifiedId): QualifiedID
    fun fromSimpleClientResponse(clientResponse: SimpleClientResponse): ClientId
    fun fromDaoModel(persistenceId: PersistenceQualifiedId): QualifiedID
    fun toApiModel(qualifiedID: QualifiedID): NetworkQualifiedId
    fun toDaoModel(qualifiedID: QualifiedID): PersistenceQualifiedId
    fun toCryptoModel(qualifiedID: QualifiedID): CryptoQualifiedID
    fun fromApiToDao(qualifiedID: NetworkQualifiedId): PersistenceQualifiedId
    fun toCryptoQualifiedIDId(qualifiedID: QualifiedID): CryptoQualifiedID
    fun fromProtoModel(qualifiedConversationID: QualifiedConversationId): ConversationId
    fun toProtoModel(conversationId: ConversationId): QualifiedConversationId
    fun toQualifiedAssetId(value: String, domain: String = ""): QualifiedID
    fun toQualifiedAssetIdEntity(value: String, domain: String = ""): PersistenceQualifiedId
}

@Suppress("TooManyFunctions")
internal class IdMapperImpl : IdMapper {

    override fun fromApiModel(networkId: NetworkQualifiedId) = QualifiedID(value = networkId.value, domain = networkId.domain)

    override fun fromSimpleClientResponse(clientResponse: SimpleClientResponse) = ClientId(clientResponse.id)

    override fun fromDaoModel(persistenceId: PersistenceQualifiedId) =
        QualifiedID(value = persistenceId.value, domain = persistenceId.domain)

    override fun toApiModel(qualifiedID: QualifiedID) = NetworkQualifiedId(value = qualifiedID.value, domain = qualifiedID.domain)

    override fun toDaoModel(qualifiedID: QualifiedID): PersistenceQualifiedId =
        PersistenceQualifiedId(value = qualifiedID.value, domain = qualifiedID.domain)

    override fun toCryptoModel(qualifiedID: QualifiedID): CryptoQualifiedID =
        CryptoQualifiedID(value = qualifiedID.value, domain = qualifiedID.domain)

    override fun fromApiToDao(qualifiedID: NetworkQualifiedId) =
        PersistenceQualifiedId(value = qualifiedID.value, domain = qualifiedID.domain)

    override fun toCryptoQualifiedIDId(qualifiedID: QualifiedID): CryptoQualifiedID =
        CryptoQualifiedID(value = qualifiedID.value, domain = qualifiedID.domain)

    override fun fromProtoModel(qualifiedConversationID: QualifiedConversationId): ConversationId =
        ConversationId(qualifiedConversationID.id, qualifiedConversationID.domain)

    override fun toProtoModel(conversationId: ConversationId): QualifiedConversationId =
        QualifiedConversationId(conversationId.value, conversationId.domain)

    override fun toQualifiedAssetId(value: String, domain: String) = QualifiedID(value, domain)

    override fun toQualifiedAssetIdEntity(value: String, domain: String) = PersistenceQualifiedId(value, domain)
}
