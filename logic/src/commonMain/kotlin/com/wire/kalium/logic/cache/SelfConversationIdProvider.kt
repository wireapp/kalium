package com.wire.kalium.logic.cache

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.user.IsMLSEnabledUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.util.DelicateKaliumApi

internal interface SelfConversationIdProvider {
    suspend operator fun invoke(): Either<StorageFailure, ConversationId>
}

internal interface ProteusSelfConversationIdProvider {
    suspend operator fun invoke(): Either<StorageFailure, ConversationId>
}

internal interface MLSSelfConversationIdProvider {
    suspend operator fun invoke(): Either<StorageFailure, ConversationId>
}

internal class SelfConversationIdProviderImpl(
    private val isMLSEnabled: IsMLSEnabledUseCase,
    private val mlsSelfConversationIdProvider: MLSSelfConversationIdProvider,
    private val proteusSelfConversationIdProvider: ProteusSelfConversationIdProvider
) : SelfConversationIdProvider {

    @OptIn(DelicateKaliumApi::class)
    override suspend fun invoke(): Either<StorageFailure, ConversationId> {
        return if (isMLSEnabled()) {
            mlsSelfConversationIdProvider()
        } else {
            proteusSelfConversationIdProvider()
        }
    }
}

internal class MLSConversationIdProviderImpl(
    private val conversationRepository: ConversationRepository
) : MLSSelfConversationIdProvider {

    private var selfConversationId: ConversationId? = null

    @OptIn(DelicateKaliumApi::class)
    override suspend fun invoke(): Either<StorageFailure, ConversationId> =
        selfConversationId?.let { Either.Right(it) }
            ?: conversationRepository.getMLSSelfConversationId().onSuccess {
                selfConversationId = it
            }
}

internal class ProteusConversationIdProviderImpl(
    private val conversationRepository: ConversationRepository
) : ProteusSelfConversationIdProvider {

    private var selfConversationId: ConversationId? = null

    @OptIn(DelicateKaliumApi::class)
    override suspend fun invoke(): Either<StorageFailure, ConversationId> =
        selfConversationId?.let { Either.Right(it) }
            ?: conversationRepository.getProteusSelfConversationId().onSuccess {
                selfConversationId = it
            }
}
