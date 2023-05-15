/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.logic.sync

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.feature.conversation.RefreshConversationsWithoutMetadataUseCase
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao.MetadataDAO
import kotlin.time.Duration.Companion.hours

/**
 * This class is responsible for checking if there are any users or conversations without metadata
 * and if so, it will refresh them.
 *
 * The criteria for this is in a window of 3 hours.
 */
interface MissingMetadataUpdateManager {
    suspend fun performSyncIfNeeded(): Either<CoreFailure, Unit>
}

internal class MissingMetadataUpdateManagerImpl(
    private val metadataDAO: MetadataDAO,
    private val refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase,
    private val refreshConversationsWithoutMetadata: RefreshConversationsWithoutMetadataUseCase
) : MissingMetadataUpdateManager {

    override suspend fun performSyncIfNeeded(): Either<CoreFailure, Unit> {
        wrapStorageRequest {
            // todo: check for last time performed
            // todo: if > 3 hrs, refresh users and conversations and update last time performed
            // todo: if not, do nothing
        }
        return Either.Right(Unit)
    }

    companion object {
        const val LAST_MISSING_METADATA_SYNC_KEY = "LAST_MISSING_METADATA_SYNC_INSTANT"
        val MIN_TIME_BETWEEN_METADATA_SYNCS = 3.hours
    }
}
