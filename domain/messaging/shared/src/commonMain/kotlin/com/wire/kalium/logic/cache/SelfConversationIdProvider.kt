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
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.data.client.MLSClientRegistrationStatusProvider
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.util.InternalKaliumApi

@InternalKaliumApi
public fun interface SelfConversationIdProvider {
    public suspend operator fun invoke(): Either<StorageFailure, List<ConversationId>>
}

@InternalKaliumApi
public fun interface ProteusSelfConversationIdProvider {
    public suspend operator fun invoke(): Either<StorageFailure, ConversationId>
}

@InternalKaliumApi
public fun interface MLSSelfConversationIdProvider {
    public suspend operator fun invoke(): Either<StorageFailure, ConversationId>
}

@InternalKaliumApi
public class SelfConversationIdProviderImpl public constructor(
    private val mlsClientRegistrationStatusProvider: MLSClientRegistrationStatusProvider,
    private val mlsSelfConversationIdProvider: MLSSelfConversationIdProvider,
    private val proteusSelfConversationIdProvider: ProteusSelfConversationIdProvider,
) : SelfConversationIdProvider {

    override suspend fun invoke(): Either<StorageFailure, List<ConversationId>> {
        val selfConversationIDs = mutableListOf(proteusSelfConversationIdProvider())

        if (mlsClientRegistrationStatusProvider().getOrElse(false)) {
            selfConversationIDs.add(mlsSelfConversationIdProvider())
        }

        return selfConversationIDs.foldToEitherWhileRight(emptyList()) { result, acc ->
            result.map {
                acc + it
            }
        }
    }
}

@InternalKaliumApi
public class MLSSelfConversationIdProviderImpl public constructor(
    conversationDAO: ConversationDAO,
) : MLSSelfConversationIdProvider {
    private val cache = SuccessOnlyCachingProvider<StorageFailure, ConversationId> {
        wrapStorageRequest { conversationDAO.getSelfConversationId(ConversationEntity.Protocol.MLS) }
            .map { it.toModel() }
    }

    override suspend fun invoke(): Either<StorageFailure, ConversationId> = cache()
}

@InternalKaliumApi
public class ProteusSelfConversationIdProviderImpl public constructor(
    conversationDAO: ConversationDAO,
) : ProteusSelfConversationIdProvider {
    private val cache = SuccessOnlyCachingProvider<StorageFailure, ConversationId> {
        wrapStorageRequest { conversationDAO.getSelfConversationId(ConversationEntity.Protocol.PROTEUS) }
            .map { it.toModel() }
    }

    override suspend fun invoke(): Either<StorageFailure, ConversationId> = cache()
}

private class SuccessOnlyCachingProvider<Error, T : Any>(
    private val getter: suspend () -> Either<Error, T>,
) {
    private var value: T? = null

    suspend operator fun invoke(): Either<Error, T> =
        value?.let { Either.Right(it) }
            ?: getter().onSuccess {
                value = it
            }
}
