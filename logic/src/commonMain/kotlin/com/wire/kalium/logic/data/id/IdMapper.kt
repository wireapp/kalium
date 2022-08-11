package com.wire.kalium.logic.data.id

import com.wire.kalium.cryptography.CryptoQualifiedID
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.network.api.UserSsoIdDTO
import com.wire.kalium.network.api.user.client.SimpleClientResponse
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.model.SsoIdEntity
import com.wire.kalium.persistence.dao.client.Client
import com.wire.kalium.protobuf.messages.QualifiedConversationId

internal typealias NetworkQualifiedId = com.wire.kalium.network.api.QualifiedID
internal typealias PersistenceQualifiedId = QualifiedIDEntity

@Suppress("TooManyFunctions")
interface IdMapper {
    fun fromApiModel(networkId: NetworkQualifiedId): QualifiedID
    fun fromSimpleClientResponse(clientResponse: SimpleClientResponse): ClientId
    fun fromClient(clientResponse: Client): ClientId
    fun fromDaoModel(persistenceId: PersistenceQualifiedId): QualifiedID
    fun toApiModel(qualifiedID: QualifiedID): NetworkQualifiedId
    fun toDaoModel(qualifiedID: QualifiedID): PersistenceQualifiedId
    fun fromDtoToDao(qualifiedID: com.wire.kalium.network.api.QualifiedID): PersistenceQualifiedId
    fun toCryptoModel(qualifiedID: QualifiedID): CryptoQualifiedID
    fun fromApiToDao(qualifiedID: NetworkQualifiedId): PersistenceQualifiedId
    fun toCryptoQualifiedIDId(qualifiedID: QualifiedID): CryptoQualifiedID
    fun fromProtoModel(qualifiedConversationID: QualifiedConversationId): ConversationId
    fun toProtoModel(conversationId: ConversationId): QualifiedConversationId
    fun toQualifiedAssetId(value: String, domain: String = ""): QualifiedID
    fun toQualifiedAssetIdEntity(value: String, domain: String = ""): PersistenceQualifiedId
    fun toSsoId(userSsoIdDTO: UserSsoIdDTO?): SsoId?
    fun toSsoIdEntity(ssoId: SsoId?): SsoIdEntity?
}

@Suppress("TooManyFunctions")
internal class IdMapperImpl : IdMapper {

    override fun fromApiModel(networkId: NetworkQualifiedId) = QualifiedID(value = networkId.value, domain = networkId.domain)

    override fun fromSimpleClientResponse(clientResponse: SimpleClientResponse) = ClientId(clientResponse.id)

    override fun fromClient(client: Client) = ClientId(client.id)

    override fun fromDaoModel(persistenceId: PersistenceQualifiedId) =
        QualifiedID(value = persistenceId.value, domain = persistenceId.domain)

    override fun toApiModel(qualifiedID: QualifiedID) = NetworkQualifiedId(value = qualifiedID.value, domain = qualifiedID.domain)

    override fun toDaoModel(qualifiedID: QualifiedID): PersistenceQualifiedId =
        PersistenceQualifiedId(value = qualifiedID.value, domain = qualifiedID.domain)

    override fun fromDtoToDao(qualifiedID: com.wire.kalium.network.api.QualifiedID): PersistenceQualifiedId =
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
    override fun toSsoId(userSsoIdDTO: UserSsoIdDTO?): SsoId? = with(userSsoIdDTO) {
        this?.let { SsoId(scimExternalId = scimExternalId, subject = subject, tenant = tenant) }
    }

    override fun toSsoIdEntity(ssoId: SsoId?): SsoIdEntity? = ssoId?.let {
        SsoIdEntity(
            tenant = ssoId.tenant,
            subject = ssoId.subject,
            scimExternalId = ssoId.scimExternalId
        )
    }
}
