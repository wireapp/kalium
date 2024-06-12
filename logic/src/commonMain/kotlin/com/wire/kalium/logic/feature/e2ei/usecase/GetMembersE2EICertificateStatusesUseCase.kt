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

import com.wire.kalium.cryptography.CryptoCertificateStatus
import com.wire.kalium.cryptography.WireIdentity
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.e2ei.CertificateStatus
import com.wire.kalium.logic.functional.fold

/**
 * This use case is used to get the e2ei certificates of all the users in Conversation.
 * Return [Map] where keys are [UserId] and values - nullable [CertificateStatus] of corresponding user.
 */
interface GetMembersE2EICertificateStatusesUseCase {
    suspend operator fun invoke(conversationId: ConversationId, userIds: List<UserId>): Map<UserId, Boolean>
}

class GetMembersE2EICertificateStatusesUseCaseImpl internal constructor(
    private val mlsConversationRepository: MLSConversationRepository
) : GetMembersE2EICertificateStatusesUseCase {
    override suspend operator fun invoke(conversationId: ConversationId, userIds: List<UserId>): Map<UserId, Boolean> =
        mlsConversationRepository.getMembersIdentities(conversationId, userIds).fold(
            { mapOf() },
            {
                it.mapValues { (_, identities) ->
                    identities.isUserMLSVerified()
                }
            }
        )
}

/**
 * @return if given user is verified or not
 */
fun List<WireIdentity>.isUserMLSVerified() = this.isEmpty() || this.any {
    it.x509Identity != null && it.status == CryptoCertificateStatus.VALID
}
