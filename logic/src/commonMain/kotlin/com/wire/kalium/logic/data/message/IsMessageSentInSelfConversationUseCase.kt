package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.functional.getOrElse

interface IsMessageSentInSelfConversationUseCase {
    suspend operator fun invoke(message: Message): Boolean
}

internal class IsMessageSentInSelfConversationUseCaseImpl(
    private val selfConversationIdProvider: SelfConversationIdProvider
) : IsMessageSentInSelfConversationUseCase {

    override suspend fun invoke(message: Message): Boolean {
        val selfConversationIds = selfConversationIdProvider().getOrElse(emptyList())
        return selfConversationIds.contains(message.conversationId)
    }

}
