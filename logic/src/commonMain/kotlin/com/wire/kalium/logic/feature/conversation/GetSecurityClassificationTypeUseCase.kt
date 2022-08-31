package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onlyRight
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.firstOrNull

fun interface GetSecurityClassificationTypeUseCase {
    /**
     * Operation that lets compute if a given conversation [conversationId] is classified or not
     *
     * @param conversationId to classify
     * @return SecurityClassificationTypeResult with classification type
     */
    suspend operator fun invoke(conversationId: ConversationId): SecurityClassificationTypeResult
}

internal class GetSecurityClassificationTypeUseCaseImpl(
    private val selfUserId: UserId,
    private val conversationRepository: ConversationRepository,
    private val userConfigRepository: UserConfigRepository
) : GetSecurityClassificationTypeUseCase {

    override suspend fun invoke(conversationId: ConversationId): SecurityClassificationTypeResult {
        val classifiedDomainsStatus = userConfigRepository.getClassifiedDomainsStatus().onlyRight().firstOrNull()
        if (classifiedDomainsStatus == null || !classifiedDomainsStatus.isClassifiedDomainsEnabled) {
            return SecurityClassificationTypeResult.Success(SecurityClassificationType.NONE)
        }

        val selfUserDomain = selfUserId.domain
        val trustedDomains = classifiedDomainsStatus.trustedDomains
        return conversationRepository.getConversationMembers(conversationId).map { participantsIds ->
            participantsIds.map { it.domain }.all { participantDomain ->
                participantDomain == selfUserDomain || trustedDomains.contains(participantDomain)
            }
        }.fold({
            kaliumLogger.e("Unexpected error while calculating conversation classified type $it")
            SecurityClassificationTypeResult.Failure(it)
        }, { isClassified ->
            when (isClassified) {
                true -> SecurityClassificationTypeResult.Success(SecurityClassificationType.CLASSIFIED)
                false -> SecurityClassificationTypeResult.Success(SecurityClassificationType.NOT_CLASSIFIED)
            }
        })
    }
}

sealed interface SecurityClassificationTypeResult {
    data class Success(val classificationType: SecurityClassificationType) : SecurityClassificationTypeResult
    data class Failure(val cause: CoreFailure) : SecurityClassificationTypeResult
}

enum class SecurityClassificationType {
    CLASSIFIED, NOT_CLASSIFIED, NONE
}
