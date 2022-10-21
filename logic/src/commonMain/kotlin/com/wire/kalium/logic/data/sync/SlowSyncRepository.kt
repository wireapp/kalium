package com.wire.kalium.logic.data.sync

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.persistence.dao.MetadataDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

internal interface SlowSyncRepository {
    val slowSyncStatus: StateFlow<SlowSyncStatus>
    suspend fun setLastSlowSyncCompletionInstant(instant: Instant)
    suspend fun clearLastSlowSyncCompletionInstant()
    suspend fun observeLastSlowSyncCompletionInstant(): Flow<Instant?>
    fun updateSlowSyncStatus(slowSyncStatus: SlowSyncStatus)
}

internal class SlowSyncRepositoryImpl(private val metadataDao: MetadataDAO) : SlowSyncRepository {
    private val logger = kaliumLogger.withFeatureId(SYNC)

    private val _slowSyncStatus = MutableStateFlow<SlowSyncStatus>(SlowSyncStatus.Pending)
    override val slowSyncStatus: StateFlow<SlowSyncStatus> get() = _slowSyncStatus.asStateFlow()

    override suspend fun setLastSlowSyncCompletionInstant(instant: Instant) {
        logger.i("Updating last slow sync instant: $instant")
        metadataDao.insertValue(value = instant.toString(), key = LAST_SLOW_SYNC_INSTANT_KEY)
    }

    override suspend fun clearLastSlowSyncCompletionInstant() {
        metadataDao.deleteValue(key = LAST_SLOW_SYNC_INSTANT_KEY)
    }

    override suspend fun observeLastSlowSyncCompletionInstant(): Flow<Instant?> =
        metadataDao.valueByKeyFlow(key = LAST_SLOW_SYNC_INSTANT_KEY)
            .map { instantString ->
                instantString?.let { Instant.parse(it) }
            }

    override fun updateSlowSyncStatus(slowSyncStatus: SlowSyncStatus) {
        logger.i("Updating SlowSync status: $slowSyncStatus")
        _slowSyncStatus.value = slowSyncStatus
    }

    private companion object {
        const val LAST_SLOW_SYNC_INSTANT_KEY = "lastSlowSyncInstant"
    }
}
