package com.wire.kalium.logic.data.sync

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
    override val lastFullSyncInstant = _lastFullSyncInstant.asStateFlow()

    private val _slowSyncStatus = MutableStateFlow<SlowSyncStatus>(SlowSyncStatus.Pending)
    override val slowSyncStatus: StateFlow<SlowSyncStatus> = _slowSyncStatus.asStateFlow()

    override fun setLastSlowSyncCompletionInstant(instant: Instant?) {
        _lastFullSyncInstant.value = instant
    }

    override fun updateSlowSyncStatus(slowSyncStatus: SlowSyncStatus) {
        _slowSyncStatus.value = slowSyncStatus
    }
}
