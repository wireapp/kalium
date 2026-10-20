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
import dev.zacsweers.metro.Qualifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex

/** Disambiguates each original root factory from cached feature captures and consumer wrappers. */
@Qualifier
internal annotation class UserSessionBinding(val value: String)

/** Prevent raw functions and Lazy values from being interpreted as Metro framework requests. */
internal class UserSessionValue<T>(val value: T)

/** Borrows existing owner state; implementations must use getters, never eager snapshots. */
internal interface UserSessionResourceInputs {
    val userStorage: UserStorage
    val authenticatedNetworkContainer: AuthenticatedNetworkContainer
    val sessionManager: SessionManager
    val globalPreferences: GlobalPrefProvider
    val userStorageProvider: UserStorageProvider
    val userAuthenticatedNetworkProvider: UserAuthenticatedNetworkProvider
    val platformUserStorageProperties: PlatformUserStorageProperties
    val resourceAccess: com.wire.kalium.util.AccountResourceAccess?
    val proteusClientProvider: ProteusClientProvider
    val mlsClientProvider: MLSClientProvider
    val networkStateObserver: NetworkStateObserver
    val dataStoragePaths: DataStoragePaths
}

/** Borrows existing owner state; implementations must use getters, never eager snapshots. */
internal interface UserSessionStateInputs {
    val userId: UserId
    val kaliumConfigs: KaliumConfigs
    val clientConfig: ClientConfig
    val userScopedLogger: KaliumLogger
    val clientIdProvider: CurrentClientIdProvider
    val selfTeamId: SelfTeamIdProvider
    val proposalTimersFlow: MutableSharedFlow<ProposalTimer>
    val mlsMutex: Mutex
    val epochChangesObserver: EpochChangesObserver
    val notificationEventsManager: NotificationEventsManager
    val localEventRepository: LocalEventRepository
    val currentPersistenceEventHookNotifier: PersistenceEventHookNotifier
    val currentCryptoStateChangeHookNotifier: CryptoStateChangeHookNotifier
    val nomadServiceUrl: String?
    val invalidateTeamId: () -> Unit
    val enableDurableMLSReceiveRecovery: Boolean
}

/** Borrows existing owner state; implementations must use getters, never eager snapshots. */
internal interface UserSessionOwnershipInputs {
    val mediaManagerService: MediaManagerService
    val flowManagerService: FlowManagerService
    val sessionCoroutineScope: CoroutineScope
    val globalCoroutineScope: CoroutineScope
    val callManager: Lazy<CallManager>
    val userSessionScopeProvider: UserSessionScopeProvider
    val userSessionWorkScheduler: UserSessionWorkScheduler
    val syncShutdownCoordinator: SyncShutdownCoordinator
    val logoutCallback: LogoutCallback
    val receiveRecovery: suspend () -> AppMLSReceiveRecovery?
}

/** Borrows existing owner state; implementations must use getters, never eager snapshots. */
internal interface UserSessionGlobalInputs {
    val globalSessionRepository: SessionRepository
    val unboundNetworkContainer: UnboundNetworkContainer
    val serverConfigRepository: ServerConfigRepository
}
