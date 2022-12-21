package com.wire.kalium.logic.feature

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Instant
import kotlin.time.Duration

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
    LAST_KEY_PACKAGE_COUNT_CHECK
}
