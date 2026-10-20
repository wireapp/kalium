/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.logic.di

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.feature.call.CallBackgroundManager
import com.wire.kalium.logic.feature.conversation.ConversationsRecoveryManager
import com.wire.kalium.logic.feature.conversation.JoinPendingMLSConversationsManager
import com.wire.kalium.logic.feature.conversation.MLSConversationsRecoveryManager
import com.wire.kalium.logic.feature.conversation.MLSFaultyKeysConversationsRepairUseCase
import com.wire.kalium.logic.feature.conversation.TypingIndicatorSyncManager
import com.wire.kalium.logic.feature.e2ei.usecase.ObserveE2EIConversationsVerificationStatusesUseCase
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.feature.message.confirmation.ConfirmationDeliveryHandler
import com.wire.kalium.logic.feature.message.ephemeral.EphemeralMessageDeletionHandler
import com.wire.kalium.logic.feature.notificationToken.PushTokenUpdater
import com.wire.kalium.logic.network.ApiMigrationManager
import com.wire.kalium.logic.sync.AvsSyncStateReporter
import com.wire.kalium.logic.sync.SyncExecutor
import com.wire.kalium.logic.sync.UserSessionWorkScheduler
import com.wire.kalium.logic.sync.deferred.MainAppPendingDeliveryWorker
import com.wire.kalium.logic.sync.local.LocalEventManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Explicit application runtime startup. Resource acquisition and Apple account preparation remain
 * with the existing platform owner. Factories preserve resolution inside each original worker's
 * coroutine, including its exception boundary and client-registration wait.
 */
@Suppress("LongParameterList")
internal class UserSessionRuntime(
    private val scope: CoroutineScope,
    private val logger: KaliumLogger,
    private val startInitialSyncMonitoring: () -> Unit,
    private val registerNomadHooks: () -> Unit,
    private val waitUntilClientIdIsAvailable: suspend () -> Unit,
    private val pendingProposalScheduler: () -> PendingProposalScheduler,
    private val apiMigrationManager: () -> ApiMigrationManager,
    private val messageRepository: () -> MessageRepository,
    private val callRepository: () -> CallRepository,
    private val pushTokenUpdater: () -> PushTokenUpdater,
    private val mlsConversationsRecoveryManager: () -> MLSConversationsRecoveryManager,
    private val joinPendingMLSConversationsManager: () -> JoinPendingMLSConversationsManager,
    private val conversationsRecoveryManager: () -> ConversationsRecoveryManager,
    private val ephemeralMessageDeletionHandler: () -> EphemeralMessageDeletionHandler,
    private val observeE2EIConversationsVerificationStatuses: () -> ObserveE2EIConversationsVerificationStatusesUseCase,
    private val typingIndicatorSyncManager: () -> TypingIndicatorSyncManager,
    private val mlsFaultyKeysConversationsRepair: () -> MLSFaultyKeysConversationsRepairUseCase,
    private val avsSyncStateReporter: () -> AvsSyncStateReporter,
    private val confirmationDeliveryHandler: () -> ConfirmationDeliveryHandler,
    private val pendingDeliveryWorker: () -> MainAppPendingDeliveryWorker,
    private val syncExecutor: () -> SyncExecutor,
    private val localEventManager: () -> LocalEventManager,
    private val workScheduler: () -> UserSessionWorkScheduler,
    private val callBackgroundManager: () -> CallBackgroundManager,
) {
    private val started = MutableStateFlow(false)

    @Suppress("TooGenericExceptionCaught")
    fun start() {
        if (!started.compareAndSet(false, true)) return
        startInitialSyncMonitoring()
        pendingProposalScheduler().start()
        registerNomadHooks()

        scope.launch {
            try {
                apiMigrationManager().performMigrations()
                messageRepository().resetAssetTransferStatus()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                logger.e("Unable to run startup migrations and reset tasks", exception)
            } finally {
                callRepository().setStaleOpenCallsCleanupFinished()
            }
        }
        scope.launch { pushTokenUpdater().monitorTokenChanges() }
        scope.launch { mlsConversationsRecoveryManager().invoke() }
        scope.launch { joinPendingMLSConversationsManager().invoke() }
        scope.launch { conversationsRecoveryManager().invoke() }
        scope.launch { ephemeralMessageDeletionHandler().enqueuePendingSelfDeletionMessages() }
        scope.launch { observeE2EIConversationsVerificationStatuses().invoke() }
        scope.launch { typingIndicatorSyncManager().execute() }
        scope.launch { mlsFaultyKeysConversationsRepair().invoke() }
        scope.launch {
            waitUntilClientIdIsAvailable()
            avsSyncStateReporter().execute()
        }
        scope.launch { confirmationDeliveryHandler().sendPendingConfirmations() }
        scope.launch { pendingDeliveryWorker().run() }

        syncExecutor().startAndStopSyncAsNeeded()
        scope.launch { localEventManager().startProcessing() }
        workScheduler().schedulePeriodicUserConfigSync()
        workScheduler().schedulePeriodicMeetingOccurrencesSync()
        scope.launch {
            waitUntilClientIdIsAvailable()
            callBackgroundManager().startProcessing()
        }
    }
}
