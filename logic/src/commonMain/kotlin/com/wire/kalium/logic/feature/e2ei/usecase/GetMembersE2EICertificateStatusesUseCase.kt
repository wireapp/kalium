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
package com.wire.kalium.logic.feature.e2ei.usecase

import com.wire.kalium.cryptography.WireIdentity
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.e2ei.CertificateStatus
import com.wire.kalium.logic.feature.e2ei.CertificateStatusMapper
import com.wire.kalium.logic.feature.e2ei.E2eiCertificate
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.map

/**
 * This use case is used to get the e2ei certificates of all the users in Conversation.
 * Return [Map] where keys are [UserId] and values - nullable [CertificateStatus] of corresponding user.
 */
interface GetMembersE2EICertificateStatusesUseCase {
    suspend operator fun invoke(conversationId: ConversationId, userIds: List<UserId>): Map<UserId, CertificateStatus?>
}

class GetMembersE2EICertificateStatusesUseCaseImpl internal constructor(
    private val mlsConversationRepository: MLSConversationRepository,
    private val certificateStatusMapper: CertificateStatusMapper,
    private val clientRepository: ClientRepository
) : GetMembersE2EICertificateStatusesUseCase {
    override suspend operator fun invoke(conversationId: ConversationId, userIds: List<UserId>): Map<UserId, CertificateStatus?> =
        mlsConversationRepository.getMembersIdentities(conversationId, userIds).flatMap { identitiesMap ->
            clientRepository.getClientsByConversationId(conversationId).map { clientsMap ->
                identitiesMap.mapValues { (userId, identities) ->
                    val clientIds = clientsMap[userId]?.filter { it.isValid }?.map { it.id.value } ?: listOf()
                    identities.getUserCertificateStatus(certificateStatusMapper, clientIds)
                }
            }
        }.getOrElse(mapOf())
}

/**
 * @return null if list is empty;
 * [CertificateStatus.REVOKED] if any certificate is revoked;
 * [CertificateStatus.EXPIRED] if any certificate is expired;
 * [CertificateStatus.VALID] otherwise.
 */
fun List<WireIdentity>.getUserCertificateStatus(
    certificateStatusMapper: CertificateStatusMapper,
    clientIds: List<String>
): CertificateStatus? {
    if (isEmpty() || clientIds.size > size || clientIds.any { clientId -> this.none { identity -> identity.clientId.value == clientId } }) {
        // there is no any certificate
        // OR no certificate for at least 1 client, which means the whole user is not certified
        return null
    }

    val certificates = this.map {
        E2eiCertificate.fromWireIdentity(it, certificateStatusMapper)
    }
    return if (certificates.any { it.status == CertificateStatus.REVOKED }) {
        CertificateStatus.REVOKED
    } else if (certificates.any { it.status == CertificateStatus.EXPIRED }) {
        CertificateStatus.EXPIRED
    } else {
        CertificateStatus.VALID
    }
}
