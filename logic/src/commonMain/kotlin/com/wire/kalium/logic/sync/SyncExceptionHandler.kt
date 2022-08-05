package com.wire.kalium.logic.sync

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class SyncExceptionHandler(
    val onCancellation: () -> Unit,
    val onFailure: (exception: CoreFailure) -> Unit
) : AbstractCoroutineContextElement(CoroutineExceptionHandler), CoroutineExceptionHandler {
    private val logger = kaliumLogger.withFeatureId(SYNC)

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        when (exception) {
            is CancellationException -> {
                logger.w("Sync job was cancelled", exception)
                onCancellation()
            }

            is KaliumSyncException -> {
                logger.i("SyncException during events processing", exception)
                onFailure(exception.coreFailureCause)
            }

            else -> {
                logger.i("Sync job failed due to unknown reason", exception)
                onFailure(CoreFailure.Unknown(exception))
            }
        }
    }
}
