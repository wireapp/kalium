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
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadAllMessagesResponse
import com.wire.kalium.network.api.base.authenticated.nomaddevice.NomadDeviceSyncApi
import com.wire.kalium.persistence.dao.backup.NomadMessageStoreResult
import com.wire.kalium.persistence.dao.backup.NomadMessagesDAO
import com.wire.kalium.userstorage.di.UserStorageProvider

/**
 * Data class representing the result of synchronizing all messages for a Nomad device.
 *
 * @property downloadedMessages The total number of messages downloaded from the API.
 * @property storedMessages The number of messages successfully stored in the local database.
 * @property skippedMessages The number of messages that were skipped during the sync process
 *                          (either due to mapping issues or storage failures).
 * @property batches The number of batches processed during the storage operation.
 */
public data class NomadAllMessagesSyncResult(
    val downloadedMessages: Int,
    val storedMessages: Int,
    val skippedMessages: Int,
    val batches: Int,
)

/**
 * Use case for synchronizing all messages from a Nomad device.
 *
 * This use case handles the complete workflow of:
 * 1. Fetching all messages from the Nomad device sync API
 * 2. Mapping the API response to domain models
 * 3. Storing the mapped messages in the local database in batches
 *
 * The class provides both an internal constructor for dependency injection testing
 * and a public constructor that integrates with the application's storage and network layers.
 *
 * @property nomadDeviceSyncApiProvider A function that provides the [NomadDeviceSyncApi] instance
 *                                      for a given [UserId].
 * @property nomadMessagesDAOProvider A function that provides the [NomadMessagesDAO] instance
 *                                    for a given [UserId], or null if the user storage is unavailable.
 * @property mapper The mapper used to convert API responses to domain models.
 *                 Defaults to [NomadAllMessagesMapper].
 * @property batchSize The size of batches used when storing messages to the database.
 *                    Defaults to [DEFAULT_BATCH_SIZE] (200).
 *
 * @see NomadAllMessagesSyncResult
 * @see NomadDeviceSyncApi
 * @see NomadMessagesDAO
 */
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
            userStorageProvider.get(userId)?.database?.nomadMessagesDAO
        },
        batchSize = batchSize
    )

    /**
     * Executes the synchronization of all Nomad device messages for the given user.
     *
     * This function performs the following steps:
     * 1. Fetches all messages from the Nomad device sync API for the specified user
     * 2. Maps the fetched API response to internal domain models
     * 3. Stores the mapped messages in the local database using the specified batch size
     *
     * If the user's storage is unavailable, the function will return a successful result
     * with all messages marked as skipped and no messages stored.
     *
     * @param selfUserId The ID of the user for which to synchronize messages.
     * @return An [Either] containing either a [CoreFailure] if the operation fails,
     *         or a [NomadAllMessagesSyncResult] with the synchronization statistics.
     */
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
        val mapped = mapper.map(response)
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

        return wrapStorageRequest {
            dao.storeMessages(
                messages = mapped.messages,
                batchSize = batchSize
            )
        }.map {
            mapResult(mapped, it)
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
