package com.wire.kalium.logic.cache

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.util.DelicateKaliumApi

internal interface SelfConversationIdProvider {
    suspend operator fun invoke(): Either<StorageFailure, List<ConversationId>>
}

internal interface ProteusSelfConversationIdProvider {
    suspend operator fun invoke(): Either<StorageFailure, ConversationId>
}

internal interface MLSSelfConversationIdProvider {
    suspend operator fun invoke(): Either<StorageFailure, ConversationId>
}

internal class SelfConversationIdProviderImpl(
    private val clientRepository: ClientRepository,
    private val mlsSelfConversationIdProvider: MLSSelfConversationIdProvider,
    private val proteusSelfConversationIdProvider: ProteusSelfConversationIdProvider
) : SelfConversationIdProvider {

    override suspend fun invoke(): Either<StorageFailure, List<ConversationId>> {
        val selfConversationIDs = mutableListOf(proteusSelfConversationIdProvider())

        if (clientRepository.hasRegisteredMLSClient().getOrElse(false)) {
            selfConversationIDs.add(mlsSelfConversationIdProvider())
        }

        return selfConversationIDs.foldToEitherWhileRight(emptyList()) { result, acc ->
            result.map {
                acc + it
            }
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
