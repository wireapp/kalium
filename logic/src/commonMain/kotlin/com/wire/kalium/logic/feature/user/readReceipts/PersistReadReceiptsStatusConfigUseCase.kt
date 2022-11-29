package com.wire.kalium.logic.feature.user.readReceipts

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.LOCAL_STORAGE
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

/**
 * UseCase that allow us to persist the configuration of read receipts to enabled or not
 */
interface PersistReadReceiptsStatusConfigUseCase {
    operator fun invoke(enabled: Boolean): ReadReceiptStatusConfigResult
}

internal class PersistReadReceiptsStatusConfigUseCaseImpl(
    val userConfigRepository: UserConfigRepository,
) : PersistReadReceiptsStatusConfigUseCase {

    private val logger by lazy { kaliumLogger.withFeatureId(LOCAL_STORAGE) }

    override fun invoke(enabled: Boolean): ReadReceiptStatusConfigResult =
        userConfigRepository.setReadReceiptsEnabled(enabled)
            .fold({
                logger.e("Failed trying to update read receipts configuration")
                ReadReceiptStatusConfigResult.Failure(it)
            }, {
                ReadReceiptStatusConfigResult.Success
            })
}

sealed class ReadReceiptStatusConfigResult {
    object Success : ReadReceiptStatusConfigResult()
    data class Failure(val cause: CoreFailure) : ReadReceiptStatusConfigResult()
}
