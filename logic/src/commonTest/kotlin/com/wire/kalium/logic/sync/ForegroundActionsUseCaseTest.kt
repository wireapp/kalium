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
import com.wire.kalium.logic.feature.conversation.mls.RecoverPendingMLSGroupJoinsUseCase
import com.wire.kalium.logic.feature.conversation.mls.RecoverPendingOneOnOneResolutionsUseCase
import com.wire.kalium.logic.feature.e2ei.SyncCertificateRevocationListUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.ObserveCertificateRevocationForSelfClientUseCase
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrationManager
import com.wire.kalium.logic.feature.server.UpdateApiVersionsUseCase
import com.wire.kalium.logic.sync.periodic.UserConfigSyncWorker
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

private val dispatchers = TestKaliumDispatcher

internal class ForegroundActionsUseCaseTest {

    @Test
    fun givenAllActionsSucceed_whenInvoked_thenAllActionsAreExecuted() = runTest(dispatchers.io) {
        val (arrangement, useCase) = arrange {}
        useCase()
        arrangement.verifyActions(times = 1)
    }

    private fun testFailedActions(actionResults: ActionResults) = runTest(dispatchers.io) {
        val (arrangement, useCase) = arrange {
            withActionResults(actionResults)
        }
        useCase()
        arrangement.verifyActions(times = 1)
    }

    @Test
    fun givenConfigSyncWorkerFails_whenInvoked_thenStillAllActionsAreExecuted() =
        testFailedActions(ActionResults(userConfigSyncWorkerResult = Result.Failure))

    private suspend fun Arrangement.verifyActions(times: Int = 1) {
        verifySuspend(VerifyMode.exactly(times)) {
            updateApiVersionsUseCase()
            userConfigSyncWorker.doWork()
            syncCertificateRevocationListUseCase()
            observeCertificateRevocationForSelfClient()
            mlsClientManager()
            mlsMigrationManager()
            keyingMaterialsManager()
            recoverPendingMLSGroupJoinsUseCase()
            recoverPendingOneOnOneResolutionsUseCase()
        }
    }

    private class Arrangement(private val configure: suspend Arrangement.() -> Unit) {
        val updateApiVersionsUseCase = mock<UpdateApiVersionsUseCase>()
        val userConfigSyncWorker = mock<UserConfigSyncWorker>()
        val syncCertificateRevocationListUseCase = mock<SyncCertificateRevocationListUseCase>()
        val observeCertificateRevocationForSelfClient = mock<ObserveCertificateRevocationForSelfClientUseCase>()
        val mlsClientManager = mock<MLSClientManager>()
        val mlsMigrationManager = mock<MLSMigrationManager>()
        val keyingMaterialsManager = mock<KeyingMaterialsManager>()
        val recoverPendingMLSGroupJoinsUseCase = mock<RecoverPendingMLSGroupJoinsUseCase>()
        val recoverPendingOneOnOneResolutionsUseCase = mock<RecoverPendingOneOnOneResolutionsUseCase>()

        suspend fun arrange(): Pair<Arrangement, ForegroundActionsUseCase> = run {
            withActionResults(ActionResults())
            configure()
            this@Arrangement to ForegroundActionsUseCaseImpl(
                updateApiVersionsUseCase = updateApiVersionsUseCase,
                userConfigSyncWorker = userConfigSyncWorker,
                syncCertificateRevocationListUseCase = syncCertificateRevocationListUseCase,
                observeCertificateRevocationForSelfClientUseCase = observeCertificateRevocationForSelfClient,
                mlsClientManager = mlsClientManager,
                mlsMigrationManager = mlsMigrationManager,
                keyingMaterialsManager = keyingMaterialsManager,
                recoverPendingMLSGroupJoinsUseCase = recoverPendingMLSGroupJoinsUseCase,
                recoverPendingOneOnOneResolutionsUseCase = recoverPendingOneOnOneResolutionsUseCase,
                dispatchers = dispatchers,
            )
        }

        suspend fun withActionResults(actionResults: ActionResults) = apply {
            everySuspend { updateApiVersionsUseCase() } returns Unit
            everySuspend { userConfigSyncWorker.doWork() } returns actionResults.userConfigSyncWorkerResult
            everySuspend { syncCertificateRevocationListUseCase() } returns Unit
            everySuspend { observeCertificateRevocationForSelfClient() } returns Unit
            everySuspend { mlsClientManager() } returns Unit
            everySuspend { mlsMigrationManager() } returns Unit
            everySuspend { keyingMaterialsManager() } returns Unit
            everySuspend { recoverPendingMLSGroupJoinsUseCase() } returns Unit
            everySuspend { recoverPendingOneOnOneResolutionsUseCase() } returns Unit
        }
    }

    data class ActionResults(
        val userConfigSyncWorkerResult: Result = Result.Success,
    )

    private companion object {
        suspend fun arrange(configure: suspend Arrangement.() -> Unit) = Arrangement(configure).arrange()
    }
}
