package com.wire.kalium.logic.sync

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.feature.server_config.UpdateApiVersionsResult
import com.wire.kalium.logic.feature.server_config.UpdateApiVersionsUseCase
import com.wire.kalium.logic.kaliumLogger


class UpdateApiVersionsWorker(
    private val apiVersionCheckManager: ApiVersionCheckManager,
    private  val updateApiVersionsUseCase: UpdateApiVersionsUseCase
) : DefaultWorker() {

    override suspend fun doWork(): Result {
        apiVersionCheckManager.changeState(ApiVersionCheckState.Running)
        return when (val result = updateApiVersionsUseCase()) {
            is UpdateApiVersionsResult.Success -> {
                kaliumLogger.e("UPDATE API VERSIONS SUCCESS ${result.serverConfigList}")
                apiVersionCheckManager.changeState(ApiVersionCheckState.Completed)
                Result.Success
            }
            is UpdateApiVersionsResult.Failure -> {
                kaliumLogger.e("UPDATE API VERSIONS FAILURE ${result.genericFailure}")
                (result.genericFailure as? CoreFailure.Unknown)?.let {
                    it.rootCause?.printStackTrace()
                }
                apiVersionCheckManager.changeState(ApiVersionCheckState.Failed(result.genericFailure))
                Result.Retry // TODO should it retry or just fail?
            }
        }
    }

    companion object {
        const val name: String = "API_VERSION_CHECK"
    }
}
