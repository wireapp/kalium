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

package com.wire.kalium.logic.data.sync

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

@Mockable
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
    suspend fun setSlowSyncVersion(version: Int)
    suspend fun getSlowSyncVersion(): Int?
}

internal class SlowSyncRepositoryImpl(
    private val metadataDao: MetadataDAO,
    logger: KaliumLogger = kaliumLogger,
) : SlowSyncRepository {
    private val logger = logger.withFeatureId(SYNC)

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

    override suspend fun setSlowSyncVersion(version: Int) {
        metadataDao.insertValue(value = version.toString(), key = SLOW_SYNC_VERSION_KEY)
    }

    override suspend fun getSlowSyncVersion(): Int? = metadataDao.valueByKey(key = SLOW_SYNC_VERSION_KEY)?.toInt()

    companion object {
        const val LAST_SLOW_SYNC_INSTANT_KEY = "lastSlowSyncInstant"
        private const val SLOW_SYNC_VERSION_KEY = "slowSyncVersion"
        private const val MLS_NEEDS_RECOVERY_KEY = "mlsNeedsRecovery"
        private const val NEEDS_TO_PERSIST_HISTORY_LOST_MESSAGES_KEY = "needsToPersistHistoryLostMessages"
    }
}
