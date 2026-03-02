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
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadAllMessagesResponse
import com.wire.kalium.network.api.base.authenticated.nomaddevice.NomadDeviceSyncApi
import com.wire.kalium.nomaddevice.dao.NomadMessageStoreResult
import com.wire.kalium.nomaddevice.dao.NomadMessagesDAO
import com.wire.kalium.nomaddevice.dao.NomadMessagesDAOImpl
import com.wire.kalium.userstorage.di.UserStorageProvider

public data class NomadAllMessagesSyncResult(
    val downloadedMessages: Int,
    val storedMessages: Int,
    val skippedMessages: Int,
    val batches: Int,
)

public class SyncNomadAllMessagesUseCase internal constructor(
    private val nomadDeviceSyncApiProvider: (UserId) -> NomadDeviceSyncApi,
    private val nomadMessagesDAOProvider: (UserId) -> NomadMessagesDAO?,
    private val mapper: NomadAllMessagesMapper = NomadAllMessagesMapper(),
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
) {

    public constructor(
        userStorageProvider: UserStorageProvider,
        nomadAuthenticatedNetworkAccess: NomadAuthenticatedNetworkAccess,
        batchSize: Int = DEFAULT_BATCH_SIZE,
    ) : this(
        nomadDeviceSyncApiProvider = { userId ->
            nomadAuthenticatedNetworkAccess.nomadDeviceSyncApi(userId.toNetworkUserId())
        },
        nomadMessagesDAOProvider = { userId ->
            userStorageProvider.get(userId)?.database?.let { database ->
                NomadMessagesDAOImpl(
                    userDAO = database.userDAO,
                    conversationDAO = database.conversationDAO,
                    messageDAO = database.messageDAO,
                )
            }
        },
        batchSize = batchSize
    )

    public suspend operator fun invoke(selfUserId: UserId): Either<CoreFailure, NomadAllMessagesSyncResult> {
        val responseResult = wrapApiRequest {
            nomadDeviceSyncApiProvider(selfUserId).getAllMessages()
        }

        return when (responseResult) {
            is Either.Left -> responseResult.value.left()
            is Either.Right -> storeFetchedMessages(selfUserId, responseResult.value)
        }
    }

    private suspend fun storeFetchedMessages(
        selfUserId: UserId,
        response: NomadAllMessagesResponse,
    ): Either<CoreFailure, NomadAllMessagesSyncResult> {
        val mapped = mapper.map(response, selfUserId)
        val dao = nomadMessagesDAOProvider(selfUserId)
        if (dao == null) {
            nomadLogger.w(
                "Skipping Nomad all-messages import: missing user storage for '${selfUserId.toLogString()}'."
            )
            return NomadAllMessagesSyncResult(
                downloadedMessages = mapped.totalMessages,
                storedMessages = 0,
                skippedMessages = mapped.totalMessages,
                batches = 0
            ).right()
        }

        return when (val storeResult = wrapStorageRequest {
            dao.storeMessages(
                selfUserId = selfUserId,
                messages = mapped.messages,
                batchSize = batchSize
            )
        }) {
            is Either.Left -> storeResult.value.left()
            is Either.Right -> mapResult(mapped, storeResult.value).right()
        }
    }

    private fun mapResult(
        mappedMessages: NomadMappedMessages,
        storeResult: NomadMessageStoreResult,
    ): NomadAllMessagesSyncResult = NomadAllMessagesSyncResult(
        downloadedMessages = mappedMessages.totalMessages,
        storedMessages = storeResult.storedMessages,
        skippedMessages = mappedMessages.skippedMessages + (mappedMessages.messages.size - storeResult.storedMessages),
        batches = storeResult.batches
    )

    private companion object {
        const val DEFAULT_BATCH_SIZE = 200
    }
}

private fun UserId.toNetworkUserId(): com.wire.kalium.network.api.model.UserId =
    com.wire.kalium.network.api.model.QualifiedID(value = value, domain = domain)
