package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.conversation.JoinExistingMLSConversationsUseCase
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

interface MLSClientManager

/**
 * MLSClientManager is responsible for registering an MLS client when a user
 * upgrades to an MLS supported build.
 */
internal class MLSClientManagerImpl(
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val featureSupport: FeatureSupport,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val clientRepository: Lazy<ClientRepository>,
    private val registerMLSClient: Lazy<RegisterMLSClientUseCase>,
    private val joinExistingMLSConversations: Lazy<JoinExistingMLSConversationsUseCase>,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : MLSClientManager {
    /**
     * A dispatcher with limited parallelism of 1.
     * This means using this dispatcher only a single coroutine will be processed at a time.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = kaliumDispatcher.default.limitedParallelism(1)

    private val scope = CoroutineScope(dispatcher)

    private var job: Job? = null

    init {
        job = scope.launch {
            incrementalSyncRepository.incrementalSyncState.collect { syncState ->
                ensureActive()
                if (syncState is IncrementalSyncStatus.Live && featureSupport.isMLSSupported) {
                    registerMLSClientIfNeeded()
                }
            }
        }
    }

    private suspend fun registerMLSClientIfNeeded() {
        clientRepository.value.hasRegisteredMLSClient().flatMap {
            if (!it) {
                currentClientIdProvider().flatMap { clientId ->
                    kaliumLogger.i("No existing MLS Client, registering..")
                    registerMLSClient.value(clientId).flatMap {
                        joinExistingMLSConversations.value()
                    }
                }
            } else {
                Either.Right(Unit)
            }
        }
    }
}
