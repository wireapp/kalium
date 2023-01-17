package com.wire.kalium.logic.feature.user.readReceipts

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.LOCAL_STORAGE
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * UseCase that allow us to persist the configuration of read receipts to enabled or not
 */
interface PersistReadReceiptsStatusConfigUseCase {
    suspend operator fun invoke(enabled: Boolean): ReadReceiptStatusConfigResult
}

internal class PersistReadReceiptsStatusConfigUseCaseImpl(
    private val userPropertyRepository: UserPropertyRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : PersistReadReceiptsStatusConfigUseCase {

    private val logger by lazy { kaliumLogger.withFeatureId(LOCAL_STORAGE) }

    override suspend fun invoke(enabled: Boolean): ReadReceiptStatusConfigResult = withContext(dispatchers.default) {
        val result = when (enabled) {
            true -> userPropertyRepository.setReadReceiptsEnabled()
            false -> userPropertyRepository.deleteReadReceiptsProperty()
        }

        result.fold({
            logger.e("Failed trying to update read receipts configuration")
            ReadReceiptStatusConfigResult.Failure(it)
        }) {
            ReadReceiptStatusConfigResult.Success
        }
    }
}

sealed class ReadReceiptStatusConfigResult {
    object Success : ReadReceiptStatusConfigResult()
    data class Failure(val cause: CoreFailure) : ReadReceiptStatusConfigResult()
}
