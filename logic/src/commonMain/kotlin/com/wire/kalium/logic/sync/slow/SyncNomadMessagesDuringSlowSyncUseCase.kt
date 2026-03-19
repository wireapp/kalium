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

package com.wire.kalium.logic.sync.slow

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.nomaddevice.NomadAuthenticatedNetworkAccess
import com.wire.kalium.nomaddevice.SyncNomadAllMessagesUseCase
import com.wire.kalium.nomaddevice.SyncNomadConversationMetadataUseCase
import com.wire.kalium.usernetwork.di.UserAuthenticatedNetworkProvider
import com.wire.kalium.userstorage.di.UserStorageProvider

internal interface SyncNomadMessagesDuringSlowSyncUseCase {
    fun isEnabled(): Boolean
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}

internal data object NoOpSyncNomadMessagesDuringSlowSyncUseCase : SyncNomadMessagesDuringSlowSyncUseCase {
    override fun isEnabled(): Boolean = false

    override suspend fun invoke(): Either<CoreFailure, Unit> = Either.Right(Unit)
}

internal class SyncNomadMessagesDuringSlowSyncUseCaseImpl(
    private val selfUserId: UserId,
    private val nomadServiceUrl: String?,
    private val userStorageProvider: UserStorageProvider,
    private val userAuthenticatedNetworkProvider: UserAuthenticatedNetworkProvider,
    private val logger: KaliumLogger,
) : SyncNomadMessagesDuringSlowSyncUseCase {

    override fun isEnabled(): Boolean = !nomadServiceUrl.isNullOrBlank()

    override suspend fun invoke(): Either<CoreFailure, Unit> {
        val nomadAuthenticatedNetworkAccess = NomadAuthenticatedNetworkAccess(userAuthenticatedNetworkProvider)
        val metadataResult = SyncNomadConversationMetadataUseCase(
            userStorageProvider = userStorageProvider,
            nomadAuthenticatedNetworkAccess = nomadAuthenticatedNetworkAccess
        )(selfUserId)

        when (metadataResult) {
            is Either.Left -> logger.w(
                "Nomad conversation-metadata slow sync failed for ${selfUserId.toLogString()}: ${metadataResult.value}"
            )
            is Either.Right ->
                logger.i(
                    "Nomad conversation-metadata slow sync finished for ${selfUserId.toLogString()}: " +
                        "downloaded=${metadataResult.value.downloadedConversations}, " +
                        "updated=${metadataResult.value.updatedConversations}, " +
                        "skipped=${metadataResult.value.skippedConversations}"
                )
        }

        val messagesResult = SyncNomadAllMessagesUseCase(
            userStorageProvider = userStorageProvider,
            nomadAuthenticatedNetworkAccess = nomadAuthenticatedNetworkAccess
        )(selfUserId)

        return when (messagesResult) {
            is Either.Left -> messagesResult
            is Either.Right -> {
                logger.i(
                    "Nomad all-messages slow sync finished for ${selfUserId.toLogString()}: " +
                        "downloaded=${messagesResult.value.downloadedMessages}, " +
                        "stored=${messagesResult.value.storedMessages}, " +
                        "skipped=${messagesResult.value.skippedMessages}, " +
                        "batches=${messagesResult.value.batches}"
                )
                Either.Right(Unit)
            }
        }
    }
}
