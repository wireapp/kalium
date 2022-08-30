package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigDataSource
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.user.GetSelfUserUseCase
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onlyRight
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

fun interface GetConversationClassifiedTypeUseCase {
    suspend operator fun invoke(conversationId: ConversationId): ClassifiedTypeResult
}

internal class GetConversationClassifiedTypeUseCaseImpl(
    private val selfUser: GetSelfUserUseCase,
    private val conversationRepository: ConversationRepository,
    private val userConfigDataSource: UserConfigDataSource
) : GetConversationClassifiedTypeUseCase {

    override suspend fun invoke(conversationId: ConversationId): ClassifiedTypeResult {
        val status = userConfigDataSource.getClassifiedDomainsStatus().onlyRight().firstOrNull()
        if (status == null || status.isClassifiedDomainsEnabled == false) {
            return ClassifiedTypeResult.Success(ClassifiedType.NONE)
        }

        val selfUser = selfUser().first()
        conversationRepository.getConversationMembers(conversationId).map {

        }
        TODO("LALA")
    }
}

sealed interface ClassifiedTypeResult {
    data class Success(val classifiedType: ClassifiedType) : ClassifiedTypeResult
    data class Failure(val cause: CoreFailure) : ClassifiedTypeResult
}

enum class ClassifiedType {
    CLASSIFIED, NOT_CLASSIFIED, NONE
}
