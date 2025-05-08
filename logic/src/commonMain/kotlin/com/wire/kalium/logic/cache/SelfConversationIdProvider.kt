/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.cache

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.util.DelicateKaliumApi
import io.mockative.Mockable

@Mockable
internal fun interface SelfConversationIdProvider {
    suspend operator fun invoke(): Either<StorageFailure, List<ConversationId>>
}

@Mockable
internal fun interface ProteusSelfConversationIdProvider {
    suspend operator fun invoke(): Either<StorageFailure, ConversationId>
}

@Mockable
internal fun interface MLSSelfConversationIdProvider {
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
