package com.wire.kalium.logic.sync

import com.wire.kalium.logic.feature.server.UpdateApiVersionsUseCase


class UpdateApiVersionsWorker(
    private val updateApiVersionsUseCase: UpdateApiVersionsUseCase
) : DefaultWorker {

    override suspend fun doWork(): Result =
        updateApiVersionsUseCase().let { Result.Success }

    companion object {
        const val name: String = "API_VERSION_CHECK"
    }
}
