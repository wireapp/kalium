package com.wire.kalium.logic.data.sync

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Instant

internal interface SlowSyncRepository {
    val lastFullSyncInstant: StateFlow<Instant?>
    val slowSyncStatus: StateFlow<SlowSyncStatus>
    fun setLastSlowSyncCompletionInstant(instant: Instant?)
    fun updateSlowSyncStatus(slowSyncStatus: SlowSyncStatus)
}

internal class InMemorySlowSyncRepository : SlowSyncRepository {
    private val _lastFullSyncInstant = MutableStateFlow<Instant?>(null)
    override val lastFullSyncInstant get() = _lastFullSyncInstant.asStateFlow()

    private val _slowSyncStatus = MutableStateFlow<SlowSyncStatus>(SlowSyncStatus.Pending)
    override val slowSyncStatus: StateFlow<SlowSyncStatus> get() = _slowSyncStatus.asStateFlow()

    override fun setLastSlowSyncCompletionInstant(instant: Instant?) {
        kaliumLogger.withFeatureId(SYNC).i("Updating last slow sync instant: $instant")
        _lastFullSyncInstant.value = instant
    }

    override fun updateSlowSyncStatus(slowSyncStatus: SlowSyncStatus) {
        kaliumLogger.withFeatureId(SYNC).i("Updating SLOW sync status: $slowSyncStatus")
        _slowSyncStatus.value = slowSyncStatus
    }
}
