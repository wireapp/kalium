package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onlyRight
import kotlinx.coroutines.flow.firstOrNull

fun interface GetConversationClassifiedTypeUseCase {
    /**
     * Operation that lets compute if a given conversation [conversationId] is classified or not
     *
     * @param conversationId to classify
     * @return ClassifiedTypeResult with classification type
     */
    suspend operator fun invoke(conversationId: ConversationId): ClassifiedTypeResult
}

internal class GetConversationClassifiedTypeUseCaseImpl(
    private val selfUserId: UserId,
    private val conversationRepository: ConversationRepository,
    private val userConfigRepository: UserConfigRepository
) : GetConversationClassifiedTypeUseCase {

    override suspend fun invoke(conversationId: ConversationId): ClassifiedTypeResult {
        val classifiedDomainsStatus = userConfigRepository.getClassifiedDomainsStatus().onlyRight().firstOrNull()
        if (classifiedDomainsStatus == null || !classifiedDomainsStatus.isClassifiedDomainsEnabled) {
            return ClassifiedTypeResult.Success(ClassifiedType.NONE)
        }

        val selfUserDomain = selfUserId.domain
        val trustedDomains = classifiedDomainsStatus.trustedDomains
        return conversationRepository.getConversationMembers(conversationId).map { participantsIds ->
            participantsIds.map { it.domain }.all { participantDomain ->
                participantDomain == selfUserDomain || trustedDomains.contains(participantDomain)
            }
        }.fold({
            ClassifiedTypeResult.Failure(it)
        }, { isClassified ->
            when (isClassified) {
                true -> ClassifiedTypeResult.Success(ClassifiedType.CLASSIFIED)
                false -> ClassifiedTypeResult.Success(ClassifiedType.NOT_CLASSIFIED)
            }
        })
    }
}

sealed interface ClassifiedTypeResult {
    data class Success(val classifiedType: ClassifiedType) : ClassifiedTypeResult
    data class Failure(val cause: CoreFailure) : ClassifiedTypeResult
}

enum class ClassifiedType {
    CLASSIFIED, NOT_CLASSIFIED, NONE
}
