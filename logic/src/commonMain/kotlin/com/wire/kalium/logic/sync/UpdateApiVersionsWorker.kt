package com.wire.kalium.logic.sync

import com.wire.kalium.logic.feature.server.UpdateApiVersionsUseCase


class UpdateApiVersionsWorker(
    private val apiVersionCheckManager: ApiVersionCheckManager,
    private val updateApiVersionsUseCase: UpdateApiVersionsUseCase
) : DefaultWorker() {

    override suspend fun doWork(): Result {
        apiVersionCheckManager.changeState(ApiVersionCheckState.Running)
        return updateApiVersionsUseCase().let { Result.Success }.also {
            apiVersionCheckManager.changeState(ApiVersionCheckState.Completed)
        }
    }

    companion object {
        const val name: String = "API_VERSION_CHECK"
    }
}
