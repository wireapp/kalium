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

    override suspend fun invoke(): Either<StorageFailure, ConversationId> {
        return if (isMLSEnabled()) {
            mlsSelfConversationIdProvider()
        } else {
            proteusSelfConversationIdProvider()
        }
    }
}

@OptIn(DelicateKaliumApi::class)
internal class MLSSelfConversationIdProviderImpl(
    val conversationRepository: ConversationRepository
) : MLSSelfConversationIdProvider, CachingProviderImpl<StorageFailure, ConversationId>({
    conversationRepository.getMLSSelfConversationId()
})

@OptIn(DelicateKaliumApi::class)
internal class ProteusSelfConversationIdProviderImpl(
    val conversationRepository: ConversationRepository
) : ProteusSelfConversationIdProvider, CachingProviderImpl<StorageFailure, ConversationId>({
    conversationRepository.getProteusSelfConversationId()
})

internal open class CachingProviderImpl<Error, T>(
    private val getter: suspend () -> Either<Error, T>
) {
    private var value: T? = null

    suspend fun invoke(): Either<Error, T> =
        value?.let { Either.Right(it) }
            ?: getter().onSuccess {
                value = it
            }
}
