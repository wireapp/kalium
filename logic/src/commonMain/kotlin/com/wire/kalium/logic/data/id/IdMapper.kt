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

package com.wire.kalium.logic.data.id

import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.CryptoQualifiedID
import com.wire.kalium.cryptography.MLSGroupId
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.client.SimpleClientResponse
import com.wire.kalium.network.api.model.UserSsoIdDTO
import com.wire.kalium.persistence.dao.client.Client
import com.wire.kalium.persistence.model.SsoIdEntity
import com.wire.kalium.protobuf.messages.QualifiedConversationId
import com.wire.kalium.protobuf.messages.QualifiedUserId
import io.mockative.Mockable
import com.wire.kalium.network.api.model.UserId as UserIdDTO

@Suppress("TooManyFunctions")
@Mockable
interface IdMapper {
    fun fromSimpleClientResponse(clientResponse: SimpleClientResponse): ClientId
    fun fromClient(client: Client): ClientId
    fun toStringDaoModel(qualifiedID: QualifiedID): String
    fun toCryptoModel(qualifiedID: QualifiedID): CryptoQualifiedID
    fun toCryptoModel(groupID: GroupID): MLSGroupId
    fun toGroupIDEntity(groupID: GroupID): String
    fun fromGroupIDEntity(groupID: String): GroupID
    fun fromCryptoModel(groupID: MLSGroupId): GroupID
    fun fromCryptoQualifiedClientId(clientId: CryptoQualifiedClientId): ClientId
    fun fromApiToDao(qualifiedID: NetworkQualifiedId): PersistenceQualifiedId
    fun toCryptoQualifiedIDId(qualifiedID: QualifiedID): CryptoQualifiedID
    fun fromProtoModel(qualifiedConversationID: QualifiedConversationId): ConversationId
    fun toProtoModel(conversationId: ConversationId): QualifiedConversationId
    fun fromProtoUserId(qualifiedUserId: QualifiedUserId): QualifiedID
    fun toProtoUserId(userId: UserId): QualifiedUserId
    fun toSsoId(userSsoIdDTO: UserSsoIdDTO?): SsoId?
    fun toSsoIdEntity(ssoId: SsoId?): SsoIdEntity?
    fun toNetworkUserId(userId: UserId): UserIdDTO

}

@Suppress("TooManyFunctions")
internal class IdMapperImpl : IdMapper {
    override fun fromSimpleClientResponse(clientResponse: SimpleClientResponse) = ClientId(clientResponse.id)

    override fun fromClient(client: Client) = ClientId(client.id)

    override fun fromGroupIDEntity(groupID: String): GroupID = GroupID(groupID)

    override fun toGroupIDEntity(groupID: GroupID): String = groupID.value

    override fun toStringDaoModel(qualifiedID: QualifiedID): String = "${qualifiedID.value}@${qualifiedID.domain}"

    override fun toCryptoModel(qualifiedID: QualifiedID): CryptoQualifiedID =
        CryptoQualifiedID(value = qualifiedID.value, domain = qualifiedID.domain)

    override fun toCryptoModel(groupID: GroupID): MLSGroupId = groupID.value

    override fun fromCryptoModel(groupID: MLSGroupId): GroupID = GroupID(groupID)

    override fun fromCryptoQualifiedClientId(clientId: CryptoQualifiedClientId): ClientId = ClientId(clientId.value)

    override fun fromApiToDao(qualifiedID: NetworkQualifiedId) =
        PersistenceQualifiedId(value = qualifiedID.value, domain = qualifiedID.domain)

    override fun toCryptoQualifiedIDId(qualifiedID: QualifiedID): CryptoQualifiedID =
        CryptoQualifiedID(value = qualifiedID.value, domain = qualifiedID.domain)

    override fun fromProtoModel(qualifiedConversationID: QualifiedConversationId): ConversationId =
        ConversationId(qualifiedConversationID.id, qualifiedConversationID.domain)

    override fun toProtoModel(conversationId: ConversationId): QualifiedConversationId =
        QualifiedConversationId(conversationId.value, conversationId.domain)

    override fun fromProtoUserId(qualifiedUserId: QualifiedUserId): UserId =
        UserId(qualifiedUserId.id, qualifiedUserId.domain)

    override fun toProtoUserId(userId: UserId): QualifiedUserId = QualifiedUserId(userId.value, userId.domain)

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

    override fun toNetworkUserId(userId: UserId): UserIdDTO =
        UserIdDTO(userId.value, userId.domain)

}
