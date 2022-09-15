package com.wire.kalium.logic.feature.server.publickeys

import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.TimestampKeyRepository
import com.wire.kalium.logic.feature.TimestampKeys
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

// The duration in hours after which we should re-check public keys.
internal val PUBLIC_KEYS_CHECK_DURATION = 24.hours

/**
 * Observes public keys last update
 */
internal interface MLSPublicKeysManager

internal class MLSPublicKeysManagerImpl(
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val fetchMLSPublicKeysUseCase: Lazy<FetchMLSPublicKeysUseCase>,
    private val timestampKeyRepository: Lazy<TimestampKeyRepository>,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : MLSPublicKeysManager {
    /**
     * A dispatcher with limited parallelism of 1.
     * This means using this dispatcher only a single coroutine will be processed at a time.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = kaliumDispatcher.default.limitedParallelism(1)

    private val fetchMLSPublicKeysScope = CoroutineScope(dispatcher)

    private var fetchMLSPublicKeysJob: Job? = null

    init {
        fetchMLSPublicKeysJob = fetchMLSPublicKeysScope.launch {
            incrementalSyncRepository.incrementalSyncState.collect { syncState ->
                ensureActive()
                if (syncState is IncrementalSyncStatus.Live) {
                    fetchPublicKeysIfNeeded()
                }
            }
        }
    }

    private suspend fun fetchPublicKeysIfNeeded() =
        timestampKeyRepository.value.hasPassed(TimestampKeys.LAST_PUBLIC_KEYS_CHECK, PUBLIC_KEYS_CHECK_DURATION)
            .flatMap { exceeded ->
                if (exceeded) {
                    fetchMLSPublicKeysUseCase.value().let { result ->
                        when (result) {
                            is FetchMLSPublicKeysResult.Failure ->
                                kaliumLogger.w("Error while fetching/updating public keys: $result")

                            is FetchMLSPublicKeysResult.Success ->
                                timestampKeyRepository.value.reset(TimestampKeys.LAST_PUBLIC_KEYS_CHECK)

                        }
                    }
                }
                Either.Right(Unit)
            }.onFailure { kaliumLogger.w("Error while fetching/updating public keys:: $it") }

}
