package com.wire.kalium.logic.feature.user.webSocketStatus

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.LOCAL_STORAGE
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.GlobalConfigRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

interface PersistPersistentWebSocketConnectionStatusUseCase {
    operator fun invoke(enabled: Boolean): Unit
}

internal class PersistPersistentWebSocketConnectionStatusUseCaseImpl(
    private val globalConfigRepository: GlobalConfigRepository
) : PersistPersistentWebSocketConnectionStatusUseCase {
    override operator fun invoke(enabled: Boolean): Unit =
        globalConfigRepository.persistPersistentWebSocketConnectionStatus(enabled).let {
            it.fold({ storageFailure ->
                when (storageFailure) {
                    is StorageFailure.DataNotFound ->
                        kaliumLogger.withFeatureId(LOCAL_STORAGE).e(
                            "DataNotFound when persisting web socket status  : $storageFailure"
                        )

                    is StorageFailure.Generic ->
                        kaliumLogger.withFeatureId(LOCAL_STORAGE).e(
                            "Failure when persisting web socket status ", storageFailure.rootCause
                        )
                }
            }, {}
            )
        }
}
