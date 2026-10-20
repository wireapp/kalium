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

package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.feature.keypackage.MLSKeyPackageCountUseCase
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesUseCase
import com.wire.kalium.logic.feature.session.DeregisterTokenUseCase
import com.wire.kalium.logic.sync.slow.RestartSlowSyncProcessForRecoveryUseCase

/** Compatibility facade over individual bindings retained by the user owner. */
public class ClientScope internal constructor(private val api: LegacyClientApi) {
    internal val clientRepository: ClientRepository
        get() = api.clientClientRepository

    internal val register: RegisterClientUseCase
        get() = api.clientRegister

    public val fetchSelfClients: FetchSelfClientsFromRemoteUseCase
        get() = api.clientFetchSelfClients

    public val fetchUsersClients: FetchUsersClientsFromRemoteUseCase
        get() = api.clientFetchUsersClients

    public val getOtherUserClients: ObserveClientsByUserIdUseCase
        get() = api.clientGetOtherUserClients

    public val observeClientDetailsUseCase: ObserveClientDetailsUseCase
        get() = api.clientObserveClientDetailsUseCase

    public val deleteClient: DeleteClientUseCase
        get() = api.clientDeleteClient

    public val needsToRegisterClient: NeedsToRegisterClientUseCase
        get() = api.clientNeedsToRegisterClient

    internal val deregisterNativePushToken: DeregisterTokenUseCase
        get() = api.clientDeregisterNativePushToken

    public val mlsKeyPackageCountUseCase: MLSKeyPackageCountUseCase
        get() = api.clientMlsKeyPackageCountUseCase

    public val restartSlowSyncProcessForRecoveryUseCase: RestartSlowSyncProcessForRecoveryUseCase
        get() = api.clientRestartSlowSyncProcessForRecoveryUseCase

    internal val refillKeyPackages: RefillKeyPackagesUseCase
        get() = api.clientRefillKeyPackages

    public val observeCurrentClientId: ObserveCurrentClientIdUseCase
        get() = api.clientObserveCurrentClientId

    internal val clearClientData: ClearClientDataUseCase
        get() = api.clientClearClientData

    public val getProteusFingerprint: GetProteusFingerprintUseCase
        get() = api.clientGetProteusFingerprint

    internal val importClient: ImportClientUseCase
        get() = api.clientImportClient

    public val getOrRegister: GetOrRegisterClientUseCase
        get() = api.clientGetOrRegister

    public val remoteClientFingerPrint: ClientFingerprintUseCase
        get() = api.clientRemoteClientFingerPrint

    public val updateClientVerificationStatus: UpdateClientVerificationStatusUseCase
        get() = api.clientUpdateClientVerificationStatus
}
