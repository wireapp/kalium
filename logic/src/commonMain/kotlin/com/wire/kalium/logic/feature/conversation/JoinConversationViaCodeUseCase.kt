package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberAddedResponse

class JoinConversationViaCodeUseCase internal constructor(
    private val conversionsGroupRepository: ConversationGroupRepository,
    private val selfUserId: UserId
) {
    suspend operator fun invoke(code: String, key: String, domain: String?): Result =
        conversionsGroupRepository.joinViaInviteCode(code, key, null)
            .fold({ failure ->
                Result.Failure(failure)
            }, { response ->
                when(response) {
                    is ConversationMemberAddedResponse.Changed -> onConversationChanged(response)
                    ConversationMemberAddedResponse.Unchanged -> onConversationUnChanged(code, key, domain)
                }
            })

    private fun onConversationChanged(response: ConversationMemberAddedResponse.Changed): Result =
        Result.Success.Changed(response.event.qualifiedConversation.toModel())

    private suspend fun onConversationUnChanged(
        code: String,
        key: String,
        domain: String?
    ): Result =
        conversionsGroupRepository.fetchLimitedInfoViaInviteCode(code, key)
            .fold({
                Result.Success.Unchanged(null)
            }, {
                ConversationId(it.nonQualifiedConversationId, domain ?: selfUserId.domain).let {conversationId ->
                    Result.Success.Unchanged(conversationId)
                }
            })

    sealed interface Result {
        sealed class Success(
            open val conversationId: ConversationId?
        ) : Result {
            data class Changed(
                override val conversationId: ConversationId,
            ) : Success(conversationId)
            data class Unchanged(
                override val conversationId: ConversationId?,
            ) : Success(conversationId)
        }

        data class Failure(
            val failure: CoreFailure
        ) : Result
    }
}
