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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.mapRight
import com.wire.kalium.common.functional.mapToRightOr
import com.wire.kalium.common.functional.onlyRight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

interface ObserveSecurityClassificationLabelUseCase {
    /**
     * Operation that lets compute if a given conversation [conversationId] in terms of compromising security or not.
     * This will observe the conversation and its participants and will return a [Flow] of [SecurityClassificationType]
     *
     * @param conversationId to classify
     * @return [Flow] of [SecurityClassificationType] with classification type
     */
    suspend operator fun invoke(conversationId: ConversationId): Flow<SecurityClassificationType>
}

internal class ObserveSecurityClassificationLabelUseCaseImpl(
    private val observeConversationMembers: ObserveConversationMembersUseCase,
    private val conversationRepository: ConversationRepository,
    private val userConfigRepository: UserConfigRepository
) : ObserveSecurityClassificationLabelUseCase {

    override suspend fun invoke(conversationId: ConversationId): Flow<SecurityClassificationType> {
        return combine(
            observeConversationMembers(conversationId),
            conversationRepository.observeConversationById(conversationId)
        ) { conversationMembers, eitherConversation -> eitherConversation.map { Pair(conversationMembers, it) } }
            .mapRight { (conversationMembers, conversation) ->
                val trustedDomains = getClassifiedDomainsStatus()
                if (trustedDomains == null) {
                    null
                } else {
                    // check if conversation domain is trusted even it doesn't contain members from it
                    trustedDomains.contains(conversation.id.domain)
                            &&
                    conversationMembers.all { memberDetails ->
                        memberDetails.user.expiresAt == null
                                && trustedDomains.contains(memberDetails.user.id.domain)
                    }
                }
            }
            .mapToRightOr(null)
            .map { isClassified ->
                when (isClassified) {
                    true -> SecurityClassificationType.CLASSIFIED
                    false -> SecurityClassificationType.NOT_CLASSIFIED
                    null -> SecurityClassificationType.NONE
                }
            }
    }

    private suspend fun getClassifiedDomainsStatus(): List<String>? {
        val classifiedDomainsStatus = userConfigRepository.getClassifiedDomainsStatus().onlyRight().firstOrNull()
        return if (classifiedDomainsStatus == null || !classifiedDomainsStatus.isClassifiedDomainsEnabled) {
            null
        } else {
            classifiedDomainsStatus.trustedDomains
        }
    }
}

enum class SecurityClassificationType {
    CLASSIFIED, NOT_CLASSIFIED, NONE
}
