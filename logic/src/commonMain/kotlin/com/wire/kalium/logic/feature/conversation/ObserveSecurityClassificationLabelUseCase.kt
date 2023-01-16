package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.onlyRight
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

fun interface ObserveSecurityClassificationLabelUseCase {
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
    private val selfUserId: UserId,
    private val conversationRepository: ConversationRepository,
    private val userConfigRepository: UserConfigRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : ObserveSecurityClassificationLabelUseCase {

    override suspend fun invoke(conversationId: ConversationId): Flow<SecurityClassificationType> = withContext(dispatcher.default) {
        conversationRepository.observeConversationMembers(conversationId)
            .map { participantsIds ->
                val trustedDomains = getClassifiedDomainsStatus()
                if (trustedDomains == null) {
                    null
                } else {
                    participantsIds.map { it.id.domain }.all { participantDomain ->
                        participantDomain == selfUserId.domain || trustedDomains.contains(participantDomain)
                    }
                }

            }.map { isClassified ->
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
