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
@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly")

package com.wire.kalium.logic.feature

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import io.mockative.Mockable
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Instant
import kotlin.time.Duration

@Mockable
interface TimestampKeyRepository {
    suspend fun hasPassed(key: TimestampKeys, duration: Duration): Either<StorageFailure, Boolean>
    suspend fun reset(key: TimestampKeys): Either<StorageFailure, Unit>
    suspend fun update(key: TimestampKeys, timestamp: Instant): Either<StorageFailure, Unit>
}

class TimestampKeyRepositoryImpl(
    private val metadataDAO: MetadataDAO
) : TimestampKeyRepository {
    override suspend fun hasPassed(key: TimestampKeys, duration: Duration): Either<StorageFailure, Boolean> =
        wrapStorageRequest {
            metadataDAO.valueByKeyFlow(key.name).firstOrNull()?.let { Instant.parse(it) } ?: Instant.DISTANT_PAST
        }.map {
            DateTimeUtil.currentInstant().minus(it) > duration
        }

    override suspend fun reset(key: TimestampKeys): Either<StorageFailure, Unit> =
        update(key, timestamp = DateTimeUtil.currentInstant())

    override suspend fun update(key: TimestampKeys, timestamp: Instant): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            metadataDAO.insertValue(timestamp.toIsoDateTimeString(), key.name)
        }
}

enum class TimestampKeys {
    LAST_KEYING_MATERIAL_UPDATE_CHECK,
    LAST_KEY_PACKAGE_COUNT_CHECK,
    LAST_MISSING_METADATA_SYNC_CHECK,
    LAST_MLS_MIGRATION_CHECK
}
