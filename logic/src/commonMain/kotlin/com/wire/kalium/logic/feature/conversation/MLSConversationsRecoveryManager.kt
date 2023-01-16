package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

internal interface MLSConversationsRecoveryManager {
    suspend fun invoke()
}

internal class MLSConversationsRecoveryManagerImpl(
    private val featureSupport: FeatureSupport,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val clientRepository: ClientRepository,
    private val recoverMLSConversationsUseCase: RecoverMLSConversationsUseCase,
    private val slowSyncRepository: SlowSyncRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : MLSConversationsRecoveryManager {

    @Suppress("ComplexCondition")
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun invoke() = withContext(dispatcher.default) {
        incrementalSyncRepository.incrementalSyncState.collect { syncState ->
            if (syncState is IncrementalSyncStatus.Live &&
                featureSupport.isMLSSupported &&
                clientRepository.hasRegisteredMLSClient().getOrElse(false) &&
                slowSyncRepository.needsToRecoverMLSGroups()
            ) {
                recoverMLSConversations()
            }
        }
    }

    private suspend fun recoverMLSConversations() =
        recoverMLSConversationsUseCase.invoke().let { result ->
            when (result) {
                is RecoverMLSConversationsResult.Failure ->
                    kaliumLogger.w("Error while recovering MLS conversations: ${result.failure}")
                is RecoverMLSConversationsResult.Success ->
                    slowSyncRepository.setNeedsToRecoverMLSGroups(false)
            }
        }
}
