package com.wire.kalium.logic.sync.periodic

import com.wire.kalium.logic.feature.server.UpdateApiVersionsUseCase
import com.wire.kalium.logic.sync.DefaultWorker
import com.wire.kalium.logic.sync.Result

class UpdateApiVersionsWorker(
    private val updateApiVersionsUseCase: UpdateApiVersionsUseCase
) : DefaultWorker {

    override suspend fun doWork(): Result =
        updateApiVersionsUseCase().let { Result.Success }

    companion object {
        const val name: String = "API_VERSION_CHECK"
    }
}
