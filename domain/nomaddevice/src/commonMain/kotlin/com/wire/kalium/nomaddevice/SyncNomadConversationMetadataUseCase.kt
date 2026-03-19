/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.nomaddevice

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadConversationMetadataResponse
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.userstorage.di.UserStorageProvider
import kotlinx.datetime.Instant

public data class NomadConversationMetadataSyncResult(
    val downloadedConversations: Int,
    val updatedConversations: Int,
    val skippedConversations: Int,
)

/**
 * Fetches conversation metadata from Nomad and applies the currently supported metadata fields locally.
 *
 * For now this restores only `last_read` for conversations that already exist in local storage.
 */
public class SyncNomadConversationMetadataUseCase internal constructor(
    private val repository: NomadConversationMetadataSyncRepository,
) {

    public constructor(
        userStorageProvider: UserStorageProvider,
        nomadAuthenticatedNetworkAccess: NomadAuthenticatedNetworkAccess,
    ) : this(
        repository = NomadConversationMetadataSyncDataSource(
            nomadDeviceSyncApiProvider = { userId ->
                nomadAuthenticatedNetworkAccess.nomadDeviceSyncApi(userId.toNetworkUserId())
            },
            metadataStoreProvider = { userId ->
                userStorageProvider.get(userId)?.database?.conversationDAO?.let(::ConversationDAONomadConversationMetadataStore)
            }
        )
    )

    public suspend operator fun invoke(selfUserId: UserId): Either<CoreFailure, NomadConversationMetadataSyncResult> {
        return when (val responseResult = repository.getConversationMetadata(selfUserId)) {
            is Either.Left -> responseResult.value.left()
            is Either.Right -> storeFetchedMetadata(selfUserId, responseResult.value)
        }
    }

    private suspend fun storeFetchedMetadata(
        selfUserId: UserId,
        response: NomadConversationMetadataResponse,
    ): Either<CoreFailure, NomadConversationMetadataSyncResult> {
        val metadata = response.conversations.map {
            NomadConversationMetadataToSync(
                conversationId = QualifiedIDEntity(
                    value = it.conversation.id,
                    domain = it.conversation.domain
                ),
                lastReadDate = Instant.fromEpochMilliseconds(it.metadata.lastRead),
                lastModifiedDate = it.metadata.lastModified?.let(Instant::fromEpochMilliseconds)
            )
        }
        return when (val storeResult = repository.applyMetadata(selfUserId, metadata)) {
            is Either.Left -> storeResult.value.left()
            is Either.Right -> NomadConversationMetadataSyncResult(
                downloadedConversations = metadata.size,
                updatedConversations = storeResult.value,
                skippedConversations = metadata.size - storeResult.value
            ).right()
        }
    }
}

/**
 * Local representation of the conversation metadata currently restored from Nomad.
 */
internal data class NomadConversationMetadataToSync(
    val conversationId: QualifiedIDEntity,
    val lastReadDate: Instant,
    val lastModifiedDate: Instant?,
)
