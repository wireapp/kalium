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
@file:OptIn(InternalKaliumApi::class, DelicateKaliumApi::class)

package com.wire.kalium.logic.di

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.EndCallOnMLSResetUseCase
import com.wire.kalium.logic.data.mls.MLSMissingUsersMessageRejectionHandler
import com.wire.kalium.logic.data.mls.MLSMissingUsersMessageRejectionHandlerImpl
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.usecase.EndCallOnMLSResetUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.UpdateConversationClientsForCurrentCallUseCase
import com.wire.kalium.logic.feature.call.usecase.UpdateConversationClientsForCurrentCallUseCaseImpl
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.logic.sync.SyncStateObserverImpl
import com.wire.kalium.logic.sync.slow.migration.SyncMigrationStepsProvider
import com.wire.kalium.logic.sync.slow.migration.SyncMigrationStepsProviderImpl
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.InternalKaliumApi
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** Deferred callbacks and lazy handles retain their original capture and acquisition timing. */
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
internal interface UserSessionDeferredBindings {
    @Provides
    @UserSessionBinding("endCallOnMLSReset")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideEndCallOnMLSReset(
        @UserSessionBinding("callRepository")
        callRepository: Provider<CallRepository>,
        @UserSessionBinding("callManager")
        callManager: Provider<UserSessionValue<Lazy<CallManager>>>,
    ): EndCallOnMLSResetUseCase =
        EndCallOnMLSResetUseCaseImpl(
            callManager = callManager().value,
            callRepository = callRepository(),
        )

    @Provides
    @UserSessionBinding("mlsMissingUsersRejectionHandlerProvider")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideMlsMissingUsersRejectionHandlerProvider(
        @UserSessionBinding("newMlsMissingUsersRejectionHandler")
        newMlsMissingUsersRejectionHandler: Provider<MLSMissingUsersMessageRejectionHandlerImpl>,
    ): UserSessionValue<() -> MLSMissingUsersMessageRejectionHandler> =
        UserSessionValue({
            newMlsMissingUsersRejectionHandler()
        })

    @Provides
    @UserSessionBinding("syncMigrationStepsProvider")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideSyncMigrationStepsProvider(
        @UserSessionBinding("syncMigrationStepsProviderSyncMigrationStepsProviderImpl")
        syncMigrationStepsProviderSyncMigrationStepsProviderImpl: Provider<SyncMigrationStepsProviderImpl>,
    ): UserSessionValue<() -> SyncMigrationStepsProvider> =
        UserSessionValue({
            syncMigrationStepsProviderSyncMigrationStepsProviderImpl()
        })

    @Provides
    @UserSessionBinding("syncStateObserver")
    @SingleIn(UserLifetime::class)
    fun userSessionProvideSyncStateObserver(
        @UserSessionBinding("syncStateObserverSyncStateObserverImpl")
        syncStateObserverSyncStateObserverImpl: Provider<SyncStateObserverImpl>,
    ): UserSessionValue<Lazy<SyncStateObserver>> =
        UserSessionValue(
            lazy {
                syncStateObserverSyncStateObserverImpl()
            }
        )

    @Provides
    @UserSessionBinding("updateConversationClientsForCurrentCall")
    fun userSessionProvideUpdateConversationClientsForCurrentCall(
        @UserSessionBinding("newUpdateConversationClientsForCurrentCall")
        newUpdateConversationClientsForCurrentCall: Provider<UpdateConversationClientsForCurrentCallUseCaseImpl>,
    ): UserSessionValue<Lazy<UpdateConversationClientsForCurrentCallUseCase>> =
        UserSessionValue(
            lazy {
                newUpdateConversationClientsForCurrentCall()
            }
        )
}
