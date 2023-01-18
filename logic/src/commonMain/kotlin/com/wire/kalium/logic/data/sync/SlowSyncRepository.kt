package com.wire.kalium.logic.data.sync

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
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
    suspend fun setNeedsToRecoverMLSGroups(value: Boolean)
    suspend fun needsToRecoverMLSGroups(): Boolean
    suspend fun setNeedsToPersistHistoryLostMessage(value: Boolean)
    suspend fun needsToPersistHistoryLostMessage(): Boolean
    suspend fun observeLastSlowSyncCompletionInstant(): Flow<Instant?>
    fun updateSlowSyncStatus(slowSyncStatus: SlowSyncStatus)
}

internal class SlowSyncRepositoryImpl(private val metadataDao: MetadataDAO) : SlowSyncRepository {
    private val logger = kaliumLogger.withFeatureId(SYNC)

    private val _slowSyncStatus = MutableStateFlow<SlowSyncStatus>(SlowSyncStatus.Pending)
    override val slowSyncStatus: StateFlow<SlowSyncStatus> get() = _slowSyncStatus.asStateFlow()

    override suspend fun setLastSlowSyncCompletionInstant(instant: Instant) {
        logger.i("Updating last slow sync instant: $instant")
        metadataDao.insertValue(value = instant.toIsoDateTimeString(), key = LAST_SLOW_SYNC_INSTANT_KEY)
    }

    override suspend fun clearLastSlowSyncCompletionInstant() {
        metadataDao.deleteValue(key = LAST_SLOW_SYNC_INSTANT_KEY)
    }

    override suspend fun setNeedsToRecoverMLSGroups(value: Boolean) {
        if (value) {
            metadataDao.insertValue(value = "true", key = MLS_NEEDS_RECOVERY_KEY)
        } else {
            metadataDao.deleteValue(key = MLS_NEEDS_RECOVERY_KEY)
        }
    }

    override suspend fun needsToRecoverMLSGroups(): Boolean {
        return metadataDao.valueByKey(key = MLS_NEEDS_RECOVERY_KEY).toBoolean()
    }

    override suspend fun setNeedsToPersistHistoryLostMessage(value: Boolean) {
        if (value) {
            metadataDao.insertValue(value = "true", key = NEEDS_TO_PERSIST_HISTORY_LOST_MESSAGES_KEY)
        } else {
            metadataDao.deleteValue(key = NEEDS_TO_PERSIST_HISTORY_LOST_MESSAGES_KEY)
        }
    }

    override suspend fun needsToPersistHistoryLostMessage(): Boolean {
        return metadataDao.valueByKey(key = NEEDS_TO_PERSIST_HISTORY_LOST_MESSAGES_KEY).toBoolean()
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
        const val MLS_NEEDS_RECOVERY_KEY = "mlsNeedsRecovery"
        const val NEEDS_TO_PERSIST_HISTORY_LOST_MESSAGES_KEY = "needsToPersistHistoryLostMessages"
    }
}
