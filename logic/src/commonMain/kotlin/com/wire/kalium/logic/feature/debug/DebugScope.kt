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

// Preserve the existing debug API network-event return type.
@file:Suppress("konsist.useCasesShouldNotAccessNetworkLayerDirectly")

package com.wire.kalium.logic.feature.debug

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.notificationToken.SendFCMTokenUseCase
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.network.api.authenticated.notification.EventResponse
import com.wire.kalium.util.DebugKaliumApi
import com.wire.kalium.util.InternalKaliumApi
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.flow.Flow

/** Compatibility facade over the individual bindings retained by the user owner. */
@DebugKaliumApi("Debug-only scope for developer tooling, QA, and profiling APIs.")
public class DebugScope internal constructor(private val api: LegacyDebugApi) {
    internal val messageRepository: MessageRepository
        get() = api.debugMessageRepository

    internal val dispatcher: KaliumDispatcher
        get() = api.debugDispatcher

    @DebugKaliumApi("Debug-only API for inspecting the active SQLCipher version.")
    public val getSqlCipherVersion: GetSqlCipherVersionUseCase
        get() = api.debugGetSqlCipherVersion

    @OptIn(InternalKaliumApi::class)
    public val establishSession: EstablishSessionUseCase
        get() = api.debugEstablishSession

    public val breakSession: BreakSessionUseCase
        get() = api.debugBreakSession

    @OptIn(InternalKaliumApi::class)
    public val sendBrokenAssetMessage: SendBrokenAssetMessageUseCase
        get() = api.debugSendBrokenAssetMessage

    @OptIn(InternalKaliumApi::class)
    public val sendConfirmation: SendConfirmationUseCase
        get() = api.debugSendConfirmation

    public val disableEventProcessing: DisableEventProcessingUseCase
        get() = api.debugDisableEventProcessing

    public val getConversationEpochFromCC: GetConversationEpochFromCCUseCase
        get() = api.debugGetConversationEpochFromCC

    public val synchronizeExternalData: SynchronizeExternalDataUseCase
        get() = api.debugSynchronizeExternalData

    internal val messageSender: MessageSender
        get() = api.debugMessageSender

    public val sendFCMTokenToServer: SendFCMTokenUseCase
        get() = api.debugSendFCMTokenToServer

    public val changeProfiling: ChangeProfilingUseCase
        get() = api.debugChangeProfiling

    public val observeDatabaseLoggerState: ObserveDatabaseLoggerStateUseCase
        get() = api.debugObserveDatabaseLoggerState

    public val optimizeDatabase: OptimizeDatabaseUseCase
        get() = api.debugOptimizeDatabase

    public val debugFeedConversationUseCase: DebugFeedConversationUseCase
        get() = api.debugDebugFeedConversationUseCase

    public val getFeatureConfig: GetFeatureConfigUseCase
        get() = api.debugGetFeatureConfig

    public val getCryptoServiceReport: GetCryptoServiceReportUseCase
        get() = api.debugGetCryptoServiceReport

    public val getDebugE2EICertificateExpiration: GetDebugE2EICertificateExpirationUseCase
        get() = api.debugGetDebugE2EICertificateExpiration

    public val setDebugE2EICertificateExpiration: SetDebugE2EICertificateExpirationUseCase
        get() = api.debugSetDebugE2EICertificateExpiration

    public val observeDebugCRLExpirationAfterOneMinute: ObserveDebugCRLExpirationAfterOneMinuteUseCase
        get() = api.debugObserveDebugCRLExpirationAfterOneMinute

    public val setDebugCRLExpirationAfterOneMinute: SetDebugCRLExpirationAfterOneMinuteUseCase
        get() = api.debugSetDebugCRLExpirationAfterOneMinute

    public val repairFaultyRemovalKeysUseCase: RepairFaultyRemovalKeysUseCase
        get() = api.debugRepairFaultyRemovalKeysUseCase

    public val getConversationCryptoStats: GetConversationCryptoStatsUseCase
        get() = api.debugGetConversationCryptoStats

    /**
     * Refills MLS key packages on the backend.
     * This is a debug utility that wraps the internal crypto transaction logic.
     */
    @OptIn(InternalKaliumApi::class)
    @Suppress("konsist.kaliumLogicModuleShouldNotExposeEitherTypesInPublicAPI")
    public suspend fun refillKeyPackages(): Either<CoreFailure, Unit> {
        return api.debugRefillKeyPackages()
    }

    /**
     * Generates test events for debugging purposes.
     * This is a debug utility that creates encrypted events between two clients.
     *
     * @param targetUserId The user ID to generate events for
     * @param targetClientId The client ID to generate events for
     * @param conversationId The conversation where events will be sent
     * @param limit The number of events to generate
     * @return A flow of generated event responses
     */
    @InternalKaliumApi
    public suspend fun generateEvents(
        targetUserId: UserId,
        targetClientId: ClientId,
        conversationId: ConversationId,
        limit: Int
    ): Flow<EventResponse> {
        return api.debugGenerateEvents(targetUserId, targetClientId, conversationId, limit)
    }
}
