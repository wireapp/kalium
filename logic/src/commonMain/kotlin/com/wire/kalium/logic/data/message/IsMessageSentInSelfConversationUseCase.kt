package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.cache.MLSSelfConversationIdProvider
import com.wire.kalium.logic.cache.ProteusSelfConversationIdProvider
import com.wire.kalium.logic.functional.getOrNull

interface IsMessageSentInSelfConversationUseCase {
    suspend operator fun invoke(message: Message): Boolean
}

internal class IsMessageSentInSelfConversationUseCaseImpl(
    private val mlsSelfConversationIdProvider: MLSSelfConversationIdProvider,
    private val proteusSelfConversationIdProvider: ProteusSelfConversationIdProvider
) : IsMessageSentInSelfConversationUseCase {

    override suspend fun invoke(message: Message): Boolean {
        val selfConversationIds = listOf(
            proteusSelfConversationIdProvider().getOrNull(),
            mlsSelfConversationIdProvider().getOrNull()
        ).mapNotNull { it }

        return selfConversationIds.contains(message.conversationId)
    }

}
