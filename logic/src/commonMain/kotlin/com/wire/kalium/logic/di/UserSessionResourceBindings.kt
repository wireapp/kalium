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

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.asset.DataStoragePaths
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.data.conversation.EpochChangesObserver
import com.wire.kalium.logic.data.conversation.ProposalTimer
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.auth.LogoutCallback
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.FlowManagerService
import com.wire.kalium.logic.feature.call.MediaManagerService
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.SyncShutdownCoordinator
import com.wire.kalium.logic.sync.UserSessionWorkScheduler
import com.wire.kalium.logic.sync.local.LocalEventRepository
import com.wire.kalium.logic.sync.receiver.conversation.message.AppMLSReceiveRecovery
import com.wire.kalium.messaging.hooks.CryptoStateChangeHookNotifier
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.networkContainer.UnboundNetworkContainer
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.usernetwork.di.UserAuthenticatedNetworkProvider
import com.wire.kalium.userstorage.di.PlatformUserStorageProperties
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.userstorage.di.UserStorageProvider
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.InternalKaliumApi
import dev.zacsweers.metro.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex

@Suppress("TooManyFunctions") // Individual projections borrow original resources and state.
internal interface UserSessionResourceBindings {
    @Provides
    @UserSessionBinding("userStorage")
    fun userSessionInputUserStorage(inputs: UserSessionResourceInputs): UserStorage =
        inputs.userStorage

    @Provides
    @UserSessionBinding("authenticatedNetworkContainer")
    fun userSessionInputAuthenticatedNetworkContainer(inputs: UserSessionResourceInputs): AuthenticatedNetworkContainer =
        inputs.authenticatedNetworkContainer

    @Provides
    @UserSessionBinding("sessionManager")
    fun userSessionInputSessionManager(inputs: UserSessionResourceInputs): SessionManager =
        inputs.sessionManager

    @Provides
    @UserSessionBinding("globalPreferences")
    fun userSessionInputGlobalPreferences(inputs: UserSessionResourceInputs): GlobalPrefProvider =
        inputs.globalPreferences

    @Provides
    @UserSessionBinding("userStorageProvider")
    fun userSessionInputUserStorageProvider(inputs: UserSessionResourceInputs): UserStorageProvider =
        inputs.userStorageProvider

    @Provides
    @UserSessionBinding("userAuthenticatedNetworkProvider")
    fun userSessionInputUserAuthenticatedNetworkProvider(inputs: UserSessionResourceInputs): UserAuthenticatedNetworkProvider =
        inputs.userAuthenticatedNetworkProvider

    @Provides
    @UserSessionBinding("platformUserStorageProperties")
    fun userSessionInputPlatformUserStorageProperties(inputs: UserSessionResourceInputs): PlatformUserStorageProperties =
        inputs.platformUserStorageProperties

    @Provides
    @UserSessionBinding("resourceAccess")
    fun userSessionInputResourceAccess(inputs: UserSessionResourceInputs): com.wire.kalium.util.AccountResourceAccess? =
        inputs.resourceAccess

    @Provides
    @UserSessionBinding("proteusClientProvider")
    fun userSessionInputProteusClientProvider(inputs: UserSessionResourceInputs): ProteusClientProvider =
        inputs.proteusClientProvider

    @Provides
    @UserSessionBinding("mlsClientProvider")
    fun userSessionInputMlsClientProvider(inputs: UserSessionResourceInputs): MLSClientProvider =
        inputs.mlsClientProvider

    @Provides
    @UserSessionBinding("networkStateObserver")
    fun userSessionInputNetworkStateObserver(inputs: UserSessionResourceInputs): NetworkStateObserver =
        inputs.networkStateObserver

    @Provides
    @UserSessionBinding("dataStoragePaths")
    fun userSessionInputDataStoragePaths(inputs: UserSessionResourceInputs): DataStoragePaths =
        inputs.dataStoragePaths

    @Provides
    @UserSessionBinding("userId")
    fun userSessionInputUserId(inputs: UserSessionStateInputs): UserId =
        inputs.userId

    @Provides
    @UserSessionBinding("kaliumConfigs")
    fun userSessionInputKaliumConfigs(inputs: UserSessionStateInputs): KaliumConfigs =
        inputs.kaliumConfigs

    @Provides
    @UserSessionBinding("clientConfig")
    fun userSessionInputClientConfig(inputs: UserSessionStateInputs): ClientConfig =
        inputs.clientConfig

    @Provides
    @UserSessionBinding("userScopedLogger")
    fun userSessionInputUserScopedLogger(inputs: UserSessionStateInputs): KaliumLogger =
        inputs.userScopedLogger

    @Provides
    @UserSessionBinding("clientIdProvider")
    fun userSessionInputClientIdProvider(inputs: UserSessionStateInputs): CurrentClientIdProvider =
        inputs.clientIdProvider

    @Provides
    @UserSessionBinding("selfTeamId")
    fun userSessionInputSelfTeamId(inputs: UserSessionStateInputs): SelfTeamIdProvider =
        inputs.selfTeamId

    @Provides
    @UserSessionBinding("proposalTimersFlow")
    fun userSessionInputProposalTimersFlow(inputs: UserSessionStateInputs): MutableSharedFlow<ProposalTimer> =
        inputs.proposalTimersFlow

    @Provides
    @UserSessionBinding("mlsMutex")
    fun userSessionInputMlsMutex(inputs: UserSessionStateInputs): Mutex =
        inputs.mlsMutex

    @Provides
    @UserSessionBinding("epochChangesObserver")
    fun userSessionInputEpochChangesObserver(inputs: UserSessionStateInputs): EpochChangesObserver =
        inputs.epochChangesObserver

    @Provides
    @UserSessionBinding("notificationEventsManager")
    fun userSessionInputNotificationEventsManager(inputs: UserSessionStateInputs): NotificationEventsManager =
        inputs.notificationEventsManager

    @Provides
    @UserSessionBinding("localEventRepository")
    fun userSessionInputLocalEventRepository(inputs: UserSessionStateInputs): LocalEventRepository =
        inputs.localEventRepository

    @Provides
    @UserSessionBinding("currentPersistenceEventHookNotifier")
    fun userSessionInputCurrentPersistenceEventHookNotifier(inputs: UserSessionStateInputs): PersistenceEventHookNotifier =
        inputs.currentPersistenceEventHookNotifier

    @Provides
    @UserSessionBinding("currentCryptoStateChangeHookNotifier")
    fun userSessionInputCurrentCryptoStateChangeHookNotifier(inputs: UserSessionStateInputs): CryptoStateChangeHookNotifier =
        inputs.currentCryptoStateChangeHookNotifier

    @Provides
    @UserSessionBinding("nomadServiceUrl")
    fun userSessionInputNomadServiceUrl(inputs: UserSessionStateInputs): String? =
        inputs.nomadServiceUrl

    @Provides
    @UserSessionBinding("invalidateTeamId")
    fun userSessionInputInvalidateTeamId(inputs: UserSessionStateInputs): UserSessionValue<() -> Unit> =
        UserSessionValue(inputs.invalidateTeamId)

    @Provides
    @UserSessionBinding("enableDurableMLSReceiveRecovery")
    fun userSessionInputEnableDurableMLSReceiveRecovery(inputs: UserSessionStateInputs): Boolean =
        inputs.enableDurableMLSReceiveRecovery

    @Provides
    @UserSessionBinding("sessionCoroutineScope")
    fun userSessionInputSessionCoroutineScope(inputs: UserSessionOwnershipInputs): CoroutineScope =
        inputs.sessionCoroutineScope

    @Provides
    @UserSessionBinding("globalCoroutineScope")
    fun userSessionInputGlobalCoroutineScope(inputs: UserSessionOwnershipInputs): CoroutineScope =
        inputs.globalCoroutineScope

    @Provides
    @UserSessionBinding("callManager")
    fun userSessionInputCallManager(inputs: UserSessionOwnershipInputs): UserSessionValue<Lazy<CallManager>> =
        UserSessionValue(inputs.callManager)

    @Provides
    @UserSessionBinding("userSessionScopeProvider")
    fun userSessionInputUserSessionScopeProvider(inputs: UserSessionOwnershipInputs): UserSessionScopeProvider =
        inputs.userSessionScopeProvider

    @Provides
    @UserSessionBinding("userSessionWorkScheduler")
    fun userSessionInputUserSessionWorkScheduler(inputs: UserSessionOwnershipInputs): UserSessionWorkScheduler =
        inputs.userSessionWorkScheduler

    @Provides
    @UserSessionBinding("syncShutdownCoordinator")
    fun userSessionInputSyncShutdownCoordinator(inputs: UserSessionOwnershipInputs): SyncShutdownCoordinator =
        inputs.syncShutdownCoordinator

    @Provides
    @UserSessionBinding("logoutCallback")
    fun userSessionInputLogoutCallback(inputs: UserSessionOwnershipInputs): LogoutCallback =
        inputs.logoutCallback

    @Provides
    @UserSessionBinding("receiveRecovery")
    fun userSessionInputReceiveRecovery(inputs: UserSessionOwnershipInputs): UserSessionValue<suspend () -> AppMLSReceiveRecovery?> =
        UserSessionValue(inputs.receiveRecovery)

    @Provides
    @UserSessionBinding("globalSessionRepository")
    fun userSessionInputGlobalSessionRepository(inputs: UserSessionGlobalInputs): SessionRepository =
        inputs.globalSessionRepository

    @Provides
    @UserSessionBinding("unboundNetworkContainer")
    fun userSessionInputUnboundNetworkContainer(inputs: UserSessionGlobalInputs): UnboundNetworkContainer =
        inputs.unboundNetworkContainer

    @Provides
    @UserSessionBinding("serverConfigRepository")
    fun userSessionInputServerConfigRepository(inputs: UserSessionGlobalInputs): ServerConfigRepository =
        inputs.serverConfigRepository

    @Provides
    @UserSessionBinding("flowManagerService")
    fun userSessionInputFlowManagerService(inputs: UserSessionOwnershipInputs): FlowManagerService =
        inputs.flowManagerService

    @Provides
    @UserSessionBinding("mediaManagerService")
    fun userSessionInputMediaManagerService(inputs: UserSessionOwnershipInputs): MediaManagerService =
        inputs.mediaManagerService

}
