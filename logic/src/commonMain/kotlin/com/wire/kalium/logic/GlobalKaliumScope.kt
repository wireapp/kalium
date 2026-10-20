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

@file:OptIn(InternalKaliumApi::class)

package com.wire.kalium.logic

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.di.GlobalGraphInputs
import com.wire.kalium.logic.di.GlobalGraphOwner
import com.wire.kalium.logic.di.KaliumGraph
import com.wire.kalium.logic.di.createKaliumGraph
import com.wire.kalium.logic.feature.appVersioning.ObserveIfAppUpdateRequiredUseCase
import com.wire.kalium.logic.feature.asset.AudioNormalizedLoudnessBuilder
import com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase
import com.wire.kalium.logic.feature.auth.LogoutCallbackManager
import com.wire.kalium.logic.feature.auth.ObserveLoginContextUseCase
import com.wire.kalium.logic.feature.auth.ValidateEmailUseCase
import com.wire.kalium.logic.feature.auth.ValidatePasswordUseCase
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCase
import com.wire.kalium.logic.feature.auth.autoVersioningAuth.AuthenticationScopeForConfigIdUseCase
import com.wire.kalium.logic.feature.auth.sso.ValidateSSOCodeUseCase
import com.wire.kalium.logic.feature.client.ClearNewClientsForUserUseCase
import com.wire.kalium.logic.feature.client.ObserveNewClientsUseCase
import com.wire.kalium.logic.feature.featureConfig.ObserveIsAppLockEditableUseCase
import com.wire.kalium.logic.feature.notificationToken.SaveNotificationTokenUseCase
import com.wire.kalium.logic.feature.server.GetServerConfigUseCase
import com.wire.kalium.logic.feature.server.IsCrossBackendLoginBlockedUseCase
import com.wire.kalium.logic.feature.server.ServerConfigForAccountUseCase
import com.wire.kalium.logic.feature.server.UpdateApiVersionsUseCase
import com.wire.kalium.logic.feature.session.DeleteSessionUseCase
import com.wire.kalium.logic.feature.session.DoesValidNomadAccountExistUseCase
import com.wire.kalium.logic.feature.session.DoesValidSessionExistUseCase
import com.wire.kalium.logic.feature.session.GetAllSessionsUseCase
import com.wire.kalium.logic.feature.session.GetSessionsUseCase
import com.wire.kalium.logic.feature.session.IsCurrentSessionNomadAccountUseCase
import com.wire.kalium.logic.feature.session.ObserveSessionsUseCase
import com.wire.kalium.logic.feature.session.SessionScope
import com.wire.kalium.logic.feature.user.ObserveValidAccountsUseCase
import com.wire.kalium.logic.feature.user.webSocketStatus.ObservePersistentWebSocketConnectionStatusUseCase
import com.wire.kalium.logic.feature.user.webSocketStatus.SetPersistentWebSocketForAllUsersUseCase
import com.wire.kalium.logic.sync.WorkSchedulerProvider
import com.wire.kalium.logic.sync.periodic.UpdateApiVersionsScheduler
import com.wire.kalium.logic.sync.periodic.UpdateApiVersionsWorker
import com.wire.kalium.network.networkContainer.UnboundNetworkContainer
import com.wire.kalium.util.InternalKaliumApi
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Scope that exposes all operations that are user and backend agnostic, like
 * - Storing and retrieving sessions after authenticating
 * - Updating client or device metadata (like push notification token)
 * - Getting back-end information from a deeplink
 *
 * @see [com.wire.kalium.logic.feature.auth.AuthenticationScope]
 * @see [com.wire.kalium.logic.feature.UserSessionScope]
 */
public class GlobalKaliumScope internal constructor(inputs: GlobalGraphInputs) : CoroutineScope {
    internal val kaliumGraph: KaliumGraph by lazy { createKaliumGraph(inputs, GlobalGraphOwner { this }) }

    override val coroutineContext: CoroutineContext = SupervisorJob()

    internal val audioNormalizedLoudnessBuilder: AudioNormalizedLoudnessBuilder
        get() = kaliumGraph.globalAudioNormalizedLoudnessBuilder

    internal val workSchedulerProvider: WorkSchedulerProvider
        get() = kaliumGraph.globalWorkSchedulerProvider

    public val logoutCallbackManager: LogoutCallbackManager
        get() = kaliumGraph.globalLogoutCallbackManager

    internal val unboundNetworkContainer: UnboundNetworkContainer
        get() = kaliumGraph.globalUnboundNetworkContainer

    internal val sessionRepository: SessionRepository
        get() = kaliumGraph.globalSessionRepository

    public val observePersistentWebSocketConnectionStatus: ObservePersistentWebSocketConnectionStatusUseCase
        get() = kaliumGraph.globalObservePersistentWebSocketConnectionStatus

    public val setAllPersistentWebSocketEnabled: SetPersistentWebSocketForAllUsersUseCase
        get() = kaliumGraph.globalSetAllPersistentWebSocketEnabled

    public val validateEmailUseCase: ValidateEmailUseCase
        get() = kaliumGraph.globalValidateEmailUseCase

    public val validateSSOCodeUseCase: ValidateSSOCodeUseCase
        get() = kaliumGraph.globalValidateSSOCodeUseCase

    public val validateUserHandleUseCase: ValidateUserHandleUseCase
        get() = kaliumGraph.globalValidateUserHandleUseCase

    public val validatePasswordUseCase: ValidatePasswordUseCase
        get() = kaliumGraph.globalValidatePasswordUseCase

    public val observeLoginContext: ObserveLoginContextUseCase
        get() = kaliumGraph.globalObserveLoginContext

    public val addAuthenticatedAccount: AddAuthenticatedUserUseCase
        get() = kaliumGraph.globalAddAuthenticatedAccount

    public val getSessions: GetSessionsUseCase
        get() = kaliumGraph.globalGetSessions

    public val getAllSessions: GetAllSessionsUseCase
        get() = kaliumGraph.globalGetAllSessions

    public val doesValidSessionExist: DoesValidSessionExistUseCase
        get() = kaliumGraph.globalDoesValidSessionExist

    public val doesValidNomadAccountExist: DoesValidNomadAccountExistUseCase
        get() = kaliumGraph.globalDoesValidNomadAccountExist

    public val isCurrentSessionNomadAccount: IsCurrentSessionNomadAccountUseCase
        get() = kaliumGraph.globalIsCurrentSessionNomadAccount

    public val observeValidAccounts: ObserveValidAccountsUseCase
        get() = kaliumGraph.globalObserveValidAccounts

    public val observeAllValidSessionsFlow: ObserveSessionsUseCase
        get() = kaliumGraph.globalObserveAllValidSessionsFlow

    internal fun createIndividualSessionApi(): com.wire.kalium.logic.di.KaliumSessionSelectionApi {
        val repository = kaliumGraph.globalSessionRepository
        return com.wire.kalium.logic.di.createSessionSelectionApi(kaliumGraph.sessionGraphFactory, repository)
    }

    public val session: SessionScope
        get() = kaliumGraph.globalSession

    public val fetchServerConfigFromDeepLink: GetServerConfigUseCase
        get() = kaliumGraph.globalFetchServerConfigFromDeepLink

    public val updateApiVersions: UpdateApiVersionsUseCase
        get() = kaliumGraph.globalUpdateApiVersions

    public val saveNotificationToken: SaveNotificationTokenUseCase
        get() = kaliumGraph.globalSaveNotificationToken

    public val deleteSession: DeleteSessionUseCase
        get() = kaliumGraph.globalDeleteSession

    public val serverConfigForAccounts: ServerConfigForAccountUseCase
        get() = kaliumGraph.globalServerConfigForAccounts

    public val isCrossBackendLoginBlocked: IsCrossBackendLoginBlockedUseCase
        get() = kaliumGraph.globalIsCrossBackendLoginBlocked

    public val authenticationScopeForConfigId: AuthenticationScopeForConfigIdUseCase
        get() = kaliumGraph.globalAuthenticationScopeForConfigId

    public val observeIfAppUpdateRequired: ObserveIfAppUpdateRequiredUseCase
        get() = kaliumGraph.globalObserveIfAppUpdateRequired

    public val observeNewClientsUseCase: ObserveNewClientsUseCase
        get() = kaliumGraph.globalObserveNewClientsUseCase

    public val clearNewClientsForUser: ClearNewClientsForUserUseCase
        get() = kaliumGraph.globalClearNewClientsForUser

    public val observeIsAppLockEditableUseCase: ObserveIsAppLockEditableUseCase
        get() = kaliumGraph.globalObserveIsAppLockEditableUseCase

    internal val updateApiVersionsWorker: UpdateApiVersionsWorker
        get() = kaliumGraph.globalUpdateApiVersionsWorker

    public val updateApiVersionsScheduler: UpdateApiVersionsScheduler
        get() = kaliumGraph.globalUpdateApiVersionsScheduler

    init {
        // Preserve the existing platform bootstrap; iOS starts periodic work explicitly.
        if (clientPlatform != "ios") {
            kaliumGraph.globalGlobalWorkScheduler.schedulePeriodicApiVersionUpdate()
        }
    }
}
