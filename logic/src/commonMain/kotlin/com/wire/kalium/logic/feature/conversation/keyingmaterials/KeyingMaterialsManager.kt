package com.wire.kalium.logic.feature.conversation.keyingmaterials

import com.wire.kalium.logic.data.sync.SyncRepository
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours

// The duration in hours after which we should re-check key package count.
/**
 * TODO: ATM we don't need that, but later we can optimise the manager by running it only every 24hours
 *   If we impl the 24hours check, then we need to check if all the update call succeed,
 *    other than that we should not consider the update as succeeded.
 */
internal val KEYING_MATERIAL_CHECK_DURATION = 24.hours

/**
 * Observes MLS conversations last keying material update
 * if a conversation's LastKeyingMaterialUpdate surpassed the threshold then
 * it'll send a new UpdateCommit for that conversation.
 */
internal interface KeyingMaterialsManager

internal class KeyingMaterialsManagerImpl(
    private val syncRepository: SyncRepository,
    private val updateKeyingMaterialsUseCase: Lazy<UpdateKeyingMaterialsUseCase>,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : KeyingMaterialsManager {
    /**
     * A dispatcher with limited parallelism of 1.
     * This means using this dispatcher only a single coroutine will be processed at a time.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = kaliumDispatcher.default.limitedParallelism(1)

    private val updateKeyingMaterialsScope = CoroutineScope(dispatcher)

    private var updateKeyingMaterialsJob: Job? = null

    init {
        updateKeyingMaterialsJob = updateKeyingMaterialsScope.launch {
            syncRepository.syncState.collect { syncState ->
                ensureActive()
                if (syncState == SyncState.Live) {
                    updateKeyingMaterialsUseCase.value().let { result ->
                        when (result) {
                            is UpdateKeyingMaterialsResult.Failure ->
                                kaliumLogger.w("Error while updating keying materials: ${result.failure}")

                            is UpdateKeyingMaterialsResult.Success -> Either.Right(Unit)
                        }
                    }
                }
            }
        }
    }
}
