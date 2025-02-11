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

import com.wire.kalium.cryptography.CredentialType
import com.wire.kalium.cryptography.CryptoCertificateStatus
import com.wire.kalium.cryptography.WireIdentity
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.mls.NameAndHandle
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.e2ei.CertificateStatus
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.map

/**
 * This use case is used to get the e2ei certificates of all the users in Conversation.
 * Return [Map] where keys are [UserId] and values - nullable [CertificateStatus] of corresponding user.
 */
interface GetMembersE2EICertificateStatusesUseCase {
    suspend operator fun invoke(conversationId: ConversationId, userIds: List<UserId>): Map<UserId, Boolean>
}

class GetMembersE2EICertificateStatusesUseCaseImpl internal constructor(
    private val mlsConversationRepository: MLSConversationRepository,
    private val conversationRepository: ConversationRepository
) : GetMembersE2EICertificateStatusesUseCase {
    override suspend operator fun invoke(conversationId: ConversationId, userIds: List<UserId>): Map<UserId, Boolean> =
        mlsConversationRepository.getMembersIdentities(conversationId, userIds)
            .map { identities ->
                val usersNameAndHandle = conversationRepository.selectMembersNameAndHandle(conversationId).getOrElse(mapOf())

                identities.mapValues { (userId, identities) ->
                    identities.isUserMLSVerified(usersNameAndHandle[userId])
                }
            }.getOrElse(mapOf())
}

/**
 * @return if given user is verified or not
 */
fun List<WireIdentity>.isUserMLSVerified(nameAndHandle: NameAndHandle?) = this.isNotEmpty() && this.all {
    it.x509Identity != null
            && it.credentialType == CredentialType.X509
            && it.status == CryptoCertificateStatus.VALID
            && it.x509Identity?.handle?.handle == nameAndHandle?.handle
            && it.x509Identity?.displayName == nameAndHandle?.name
}
