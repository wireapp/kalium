package com.wire.kalium.logic.cache

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.util.DelicateKaliumApi

internal interface SelfConversationIdProvider {
    suspend operator fun invoke(): Either<StorageFailure, ConversationId>
}

internal class SelfConversationIdProviderImpl(
    private val conversationRepository: ConversationRepository
) : SelfConversationIdProvider {

    private var _selfConversationId: ConversationId? = null

    @OptIn(DelicateKaliumApi::class)
    override suspend fun invoke(): Either<StorageFailure, ConversationId> =
        if (_selfConversationId != null) Either.Right(_selfConversationId!!)
        else {
            conversationRepository.getSelfConversationId().onSuccess {
                _selfConversationId = it
            }
        }
}
