/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.conversation.keyingmaterials

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration

class RenewMLSKeyingMaterialsUseCaseTest {

    @Test
    fun givenEstablishedGroups_whenRenewing_thenEveryGroupGetsAnUpdateCommitRegardlessOfItsAge() = runTest {
        val (arrangement, renewKeyingMaterials) = Arrangement()
            .withSyncLive()
            .withGroups(Either.Right(Arrangement.GROUPS))
            .withUpdateSuccessful()
            .arrange()

        val result = renewKeyingMaterials()

        assertEquals(RenewMLSKeyingMaterialsResult.Success(renewedGroups = 3, failedGroups = 0), result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.getMLSGroupsRequiringKeyingMaterialUpdate(eq(Duration.ZERO))
        }
        verifySuspend(VerifyMode.exactly(Arrangement.GROUPS.size)) {
            arrangement.mlsConversationRepository.updateKeyingMaterial(any(), any())
        }
    }

    @Test
    fun givenOneGroupFails_whenRenewing_thenTheOtherGroupsAreStillRenewed() = runTest {
        val (arrangement, renewKeyingMaterials) = Arrangement()
            .withSyncLive()
            .withGroups(Either.Right(Arrangement.GROUPS))
            .withUpdateFailingFor(Arrangement.GROUPS[0], StorageFailure.DataNotFound)
            .arrange()

        val result = renewKeyingMaterials()

        assertEquals(RenewMLSKeyingMaterialsResult.Success(renewedGroups = 2, failedGroups = 1), result)
        verifySuspend(VerifyMode.exactly(Arrangement.GROUPS.size)) {
            arrangement.mlsConversationRepository.updateKeyingMaterial(any(), any())
        }
    }

    @Test
    fun givenNoNetwork_whenRenewing_thenItStopsAndFails() = runTest {
        val (arrangement, renewKeyingMaterials) = Arrangement()
            .withSyncLive()
            .withGroups(Either.Right(Arrangement.GROUPS))
            .withUpdateFailingFor(Arrangement.GROUPS[0], NetworkFailure.NoNetworkConnection(IOException("No network")))
            .arrange()

        val result = renewKeyingMaterials()

        assertIs<RenewMLSKeyingMaterialsResult.Failure>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.updateKeyingMaterial(any(), any())
        }
    }

    @Test
    fun givenSyncDoesNotBecomeLive_whenRenewing_thenNoGroupIsTouched() = runTest {
        val (arrangement, renewKeyingMaterials) = Arrangement()
            .withSyncFailing(NetworkFailure.NoNetworkConnection(null))
            .arrange()

        val result = renewKeyingMaterials()

        assertIs<RenewMLSKeyingMaterialsResult.Failure>(result)
        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.getMLSGroupsRequiringKeyingMaterialUpdate(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.updateKeyingMaterial(any(), any())
        }
    }

    @Test
    fun givenGroupsCannotBeRead_whenRenewing_thenItFails() = runTest {
        val (arrangement, renewKeyingMaterials) = Arrangement()
            .withSyncLive()
            .withGroups(Either.Left(StorageFailure.DataNotFound))
            .arrange()

        val result = renewKeyingMaterials()

        assertIs<RenewMLSKeyingMaterialsResult.Failure>(result)
        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.updateKeyingMaterial(any(), any())
        }
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

        val syncManager = mock<SyncManager>()
        val mlsConversationRepository = mock<MLSConversationRepository>(mode = MockMode.autoUnit)

        suspend fun withSyncLive() = apply {
            everySuspend { syncManager.waitUntilLiveOrFailure() } returns Either.Right(Unit)
        }

        suspend fun withSyncFailing(failure: CoreFailure) = apply {
            everySuspend { syncManager.waitUntilLiveOrFailure() } returns Either.Left(failure)
        }

        suspend fun withGroups(result: Either<CoreFailure, List<GroupID>>) = apply {
            everySuspend { mlsConversationRepository.getMLSGroupsRequiringKeyingMaterialUpdate(any()) } returns result
        }

        suspend fun withUpdateSuccessful() = apply {
            everySuspend { mlsConversationRepository.updateKeyingMaterial(any(), any()) } returns Either.Right(Unit)
        }

        suspend fun withUpdateFailingFor(failedGroup: GroupID, failure: CoreFailure) = apply {
            everySuspend { mlsConversationRepository.updateKeyingMaterial(any(), eq(failedGroup)) } returns Either.Left(failure)
            everySuspend {
                mlsConversationRepository.updateKeyingMaterial(any(), matching { it != failedGroup })
            } returns Either.Right(Unit)
        }

        suspend fun arrange() = this to RenewMLSKeyingMaterialsUseCaseImpl(
            syncManager,
            mlsConversationRepository,
            cryptoTransactionProvider
        ).also {
            withMLSTransactionReturning(Either.Right(Unit))
        }

        companion object {
            val GROUPS = listOf(GroupID("group1"), GroupID("group2"), GroupID("group3"))
        }
    }
}
