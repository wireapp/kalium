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
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadConversationMetadataResponse
import com.wire.kalium.network.api.base.authenticated.nomaddevice.NomadDeviceSyncApi
import com.wire.kalium.persistence.dao.conversation.ConversationDAO

internal interface NomadConversationMetadataSyncRepository {
    suspend fun getConversationMetadata(selfUserId: UserId): Either<CoreFailure, NomadConversationMetadataResponse>
    suspend fun applyMetadata(
        selfUserId: UserId,
        metadata: List<NomadConversationMetadataToSync>,
    ): Either<CoreFailure, Int>
}

internal class NomadConversationMetadataSyncDataSource(
    private val nomadDeviceSyncApiProvider: (UserId) -> NomadDeviceSyncApi,
    private val metadataStoreProvider: (UserId) -> NomadConversationMetadataStore?,
) : NomadConversationMetadataSyncRepository {

    override suspend fun getConversationMetadata(selfUserId: UserId): Either<CoreFailure, NomadConversationMetadataResponse> =
        wrapApiRequest {
            nomadDeviceSyncApiProvider(selfUserId).getConversationMetadata()
        }

    override suspend fun applyMetadata(
        selfUserId: UserId,
        metadata: List<NomadConversationMetadataToSync>,
    ): Either<CoreFailure, Int> {
        val metadataStore = metadataStoreProvider(selfUserId)
        if (metadataStore == null) {
            nomadLogger.w(
                "Skipping Nomad conversation-metadata import: missing user storage for '${selfUserId.toLogString()}'."
            )
            return 0.right()
        }

        return wrapStorageRequest {
            metadataStore.applyMetadata(metadata)
        }
    }
}

/**
 * Applies fetched Nomad conversation metadata to local storage.
 */
internal interface NomadConversationMetadataStore {
    suspend fun applyMetadata(metadata: List<NomadConversationMetadataToSync>): Int
}

/**
 * ConversationDAO-backed metadata store used during Nomad login restore.
 */
internal class ConversationDAONomadConversationMetadataStore(
    private val conversationDAO: ConversationDAO,
) : NomadConversationMetadataStore {

    override suspend fun applyMetadata(metadata: List<NomadConversationMetadataToSync>): Int =
        conversationDAO.updateConversationReadAndModifiedDates(
            readDates = metadata.associate { it.conversationId to it.lastReadDate },
            modifiedDates = metadata.mapNotNull { entry ->
                entry.lastModifiedDate?.let { entry.conversationId to it }
            }.toMap()
        )
}
