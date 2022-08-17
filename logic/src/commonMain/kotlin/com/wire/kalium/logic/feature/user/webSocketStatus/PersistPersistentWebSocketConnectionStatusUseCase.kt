package com.wire.kalium.logic.feature.user.webSocketStatus

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.LOCAL_STORAGE

interface PersistPersistentWebSocketConnectionStatusUseCase {
    operator fun invoke(enabled: Boolean): Unit
}

internal class PersistPersistentWebSocketConnectionStatusUseCaseImpl(
    private val userConfigRepository: UserConfigRepository
) : PersistPersistentWebSocketConnectionStatusUseCase {
    override operator fun invoke(enabled: Boolean): Unit =
        userConfigRepository.persistPersistentWebSocketConnectionStatus(enabled).let {
            it.fold({ storageFailure ->
                when (storageFailure) {
                    is StorageFailure.DataNotFound ->
                        kaliumLogger.withFeatureId(LOCAL_STORAGE).e("DataNotFound when persisting web socket status  : $storageFailure")

                    is StorageFailure.Generic ->
                        kaliumLogger.withFeatureId(LOCAL_STORAGE).e("Failure when persisting web socket status ", storageFailure.rootCause)
                }
            }, {}
            )
        }
}
