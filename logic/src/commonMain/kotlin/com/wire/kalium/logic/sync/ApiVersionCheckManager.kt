package com.wire.kalium.logic.sync

import com.wire.kalium.logic.CoreFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

enum class ApiVersionCheckResult {
    CLIENT_TOO_OLD,
    CLIENT_TOO_NEW,
    COMPATIBLE
}

sealed class ApiVersionCheckState {
    object Waiting: ApiVersionCheckState()
    object Running: ApiVersionCheckState()
    data class Completed(val result: ApiVersionCheckResult): ApiVersionCheckState()
    data class Failed(val failure: CoreFailure): ApiVersionCheckState()
}

interface ApiVersionCheckManager {

    fun changeState(newState: ApiVersionCheckState)
    fun startIfNotAlreadyCompletedOrRunning()
    fun schedulePeriodicCheck()
    fun currentStateFlow(): Flow<ApiVersionCheckState>
    suspend fun waitUntilCompleted(): ApiVersionCheckState
}

class ApiVersionCheckManagerImpl(private val workScheduler: WorkScheduler.Global) : ApiVersionCheckManager {

    private val state = MutableStateFlow<ApiVersionCheckState>(ApiVersionCheckState.Waiting)

    override fun changeState(newState: ApiVersionCheckState) {
        state.update { newState }
    }

    override fun startIfNotAlreadyCompletedOrRunning() {
        val currentState = state.getAndUpdate {
            when (it) {
                ApiVersionCheckState.Waiting -> ApiVersionCheckState.Running
                else -> it
            }
        }
        if (currentState is ApiVersionCheckState.Waiting) {
            workScheduler.scheduleImmediateApiVersionCheck()
        }
    }

    override fun schedulePeriodicCheck() {
        workScheduler.schedulePeriodicApiVersionCheck()
    }

    override suspend fun waitUntilCompleted(): ApiVersionCheckState = state.first { it is ApiVersionCheckState.Completed }

    override fun currentStateFlow(): Flow<ApiVersionCheckState> = state
}
