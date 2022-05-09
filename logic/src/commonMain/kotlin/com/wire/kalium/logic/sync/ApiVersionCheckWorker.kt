package com.wire.kalium.logic.sync


class ApiVersionCheckWorker(
    private val apiVersionCheckManager: ApiVersionCheckManager
) : DefaultWorker() {

    override suspend fun doWork(): Result {
        apiVersionCheckManager.changeState(ApiVersionCheckState.Running)
//        return apiVersionRepository.fetchApiVersion()
//            .fold({
//                apiVersionCheckManager.changeState(ApiVersionCheckState.Failed(it))
//                Result.Retry
//            }, {
//                // TODO change to proper implementation: compare with app's supported APIs and store the common version
//                apiVersionCheckManager.changeState(ApiVersionCheckState.Completed(ApiVersionCheckResult.COMPATIBLE))
//                Result.Success
//            })
        return Result.Success // TODO
    }

    companion object {
        const val name: String = "API_VERSION_CHECK"
    }
}
