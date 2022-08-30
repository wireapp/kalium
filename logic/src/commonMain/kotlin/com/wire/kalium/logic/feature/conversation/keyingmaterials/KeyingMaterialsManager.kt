package com.wire.kalium.logic.feature.conversation.keyingmaterials

import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.TimestampKeyRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.onFailure
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
internal val KEYING_MATERIAL_CHECK_DURATION = 24.hours
internal const val LAST_KEYING_MATERIAL_UPDATE_CHECK = "LAST_KEYING_MATERIAL_UPDATE_CHECK"

/**
 * Observes MLS conversations last keying material update
 * if a conversation's LastKeyingMaterialUpdate surpassed the threshold then
 * it'll send a new UpdateCommit for that conversation.
 */
internal interface KeyingMaterialsManager

internal class KeyingMaterialsManagerImpl(
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val updateKeyingMaterialsUseCase: Lazy<UpdateKeyingMaterialsUseCase>,
    private val timestampKeyRepository: Lazy<TimestampKeyRepository>,
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
            incrementalSyncRepository.incrementalSyncState.collect { syncState ->
                ensureActive()
                if (syncState is IncrementalSyncStatus.Live) {
                    updateKeyingMaterialIfNeeded()
                }
            }
        }
    }

    private suspend fun updateKeyingMaterialIfNeeded() =
        timestampKeyRepository.value.isPassed(LAST_KEYING_MATERIAL_UPDATE_CHECK, KEYING_MATERIAL_CHECK_DURATION)
            .flatMap { exceeded ->
                if (exceeded) {
                    updateKeyingMaterialsUseCase.value().let { result ->
                        when (result) {
                            is UpdateKeyingMaterialsResult.Failure ->
                                kaliumLogger.w("Error while updating keying materials: ${result.failure}")

                            is UpdateKeyingMaterialsResult.Success ->
                                timestampKeyRepository.value.reset(LAST_KEYING_MATERIAL_UPDATE_CHECK)
                        }
                    }
                }
                Either.Right(Unit)
            }.onFailure { kaliumLogger.w("Error while updating keying materials:: $it") }

}
