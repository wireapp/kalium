/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.sync

import com.wire.kalium.logic.feature.client.MLSClientManager
import com.wire.kalium.logic.feature.conversation.keyingmaterials.KeyingMaterialsManager
import com.wire.kalium.logic.feature.e2ei.SyncCertificateRevocationListUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.ObserveCertificateRevocationForSelfClientUseCase
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrationManager
import com.wire.kalium.logic.feature.server.UpdateApiVersionsUseCase
import com.wire.kalium.logic.sync.periodic.UserConfigSyncWorker
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import io.mockative.Mockable
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Use case that combines all actions that should be performed when the app launches, gains focus (desktop/web),
 * or changes its state to foreground (mobile).
 */
@Mockable
internal interface ForegroundActionsUseCase {
    suspend operator fun invoke()
}

@Suppress("LongParameterList")
internal class ForegroundActionsUseCaseImpl(
    private val updateApiVersionsUseCase: UpdateApiVersionsUseCase,
    private val userConfigSyncWorker: UserConfigSyncWorker,
    private val syncCertificateRevocationListUseCase: SyncCertificateRevocationListUseCase,
    private val observeCertificateRevocationForSelfClientUseCase: ObserveCertificateRevocationForSelfClientUseCase,
    private val mlsClientManager: MLSClientManager,
    private val mlsMigrationManager: MLSMigrationManager,
    private val keyingMaterialsManager: KeyingMaterialsManager,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
) : ForegroundActionsUseCase {

    private val actions: List<suspend () -> Unit> = listOf(
        { updateApiVersionsUseCase() },
        { userConfigSyncWorker.doWork() },
        { syncCertificateRevocationListUseCase() },
        { observeCertificateRevocationForSelfClientUseCase() },
        { mlsClientManager() },
        { mlsMigrationManager() },
        { keyingMaterialsManager() },
    )

    override suspend operator fun invoke() = withContext(dispatchers.io) {
        actions.map { action ->
            launch(dispatchers.io) { action() }
        }.joinAll()
    }
}
