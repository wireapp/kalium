package com.wire.kalium.logic.feature

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao.MetadataDAO
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

interface TimestampKeyRepository {
    suspend fun isPassed(key: String, duration: Duration): Either<StorageFailure, Boolean>
    suspend fun reset(key: String): Either<StorageFailure, Unit>
    suspend fun update(key: String, timestamp: Instant): Either<StorageFailure, Unit>
}

class TimestampKeyRepositoryImpl(
    private val metadataDAO: MetadataDAO
) : TimestampKeyRepository {
    override suspend fun isPassed(key: String, duration: Duration): Either<StorageFailure, Boolean> =
        wrapStorageRequest {
            metadataDAO.valueByKeyFlow(key).firstOrNull()?.let { Instant.parse(it) } ?: Instant.DISTANT_PAST
        }.map {
            Clock.System.now().minus(it) > duration
        }

    override suspend fun reset(key: String): Either<StorageFailure, Unit> =
        update(key, timestamp = Clock.System.now())


    override suspend fun update(key: String, timestamp: Instant): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            metadataDAO.insertValue(timestamp.toString(), key)
        }


}
