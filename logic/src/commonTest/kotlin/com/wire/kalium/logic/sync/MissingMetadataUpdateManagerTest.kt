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
package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.TimestampKeyRepository
import com.wire.kalium.logic.feature.conversation.RefreshConversationsWithoutMetadataUseCase
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test

class MissingMetadataUpdateManagerTest {

    @Test
    fun givenLastCheckAfterDuration_whenObservingFinishes_metadataSyncIsPerformed() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, _) = Arrangement()
                .withLastMetadataSyncKeyCheck(true)
                .withLastMetadataSyncKeyResetCheckSuccessful()
                .withRefreshUsersSuccess()
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.refreshConversationsWithoutMetadataUseCase.invoke()
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.refreshUsersWithoutMetadataUseCase.invoke()
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.timestampKeyRepository.reset(any())
            }
        }

    @Test
    fun givenLastCheckBeforeDuration_whenObservingFinishes_metadataSyncIsNOTPerformed() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, _) = Arrangement()
                .withLastMetadataSyncKeyCheck(false)
                .withLastMetadataSyncKeyResetCheckSuccessful()
                .withRefreshUsersSuccess()
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()

            verifySuspend(VerifyMode.not) {
                arrangement.refreshConversationsWithoutMetadataUseCase.invoke()
            }

            verifySuspend(VerifyMode.not) {
                arrangement.refreshUsersWithoutMetadataUseCase.invoke()
            }

            verifySuspend(VerifyMode.not) {
                arrangement.timestampKeyRepository.reset(any())
            }
        }

    private class Arrangement {

        val incrementalSyncRepository: IncrementalSyncRepository = InMemoryIncrementalSyncRepository()
        val timestampKeyRepository = mock<TimestampKeyRepository>(mode = MockMode.autoUnit)
        val refreshConversationsWithoutMetadataUseCase = mock<RefreshConversationsWithoutMetadataUseCase>(mode = MockMode.autoUnit)
        val refreshUsersWithoutMetadataUseCase = mock<RefreshUsersWithoutMetadataUseCase>(mode = MockMode.autoUnit)

        suspend fun withLastMetadataSyncKeyCheck(hasPassed: Boolean) = apply {
            everySuspend {
                timestampKeyRepository.hasPassed(any(), any())
            } returns Either.Right(hasPassed)
        }

        suspend fun withLastMetadataSyncKeyResetCheckSuccessful() = apply {
            everySuspend {
                timestampKeyRepository.reset(any())
            } returns Either.Right(Unit)
        }

        suspend fun withRefreshUsersSuccess() = apply {
            everySuspend {
                refreshUsersWithoutMetadataUseCase.invoke()
            } returns Unit
        }

        fun arrange() = this to MissingMetadataUpdateManagerImpl(
            incrementalSyncRepository,
            lazy { refreshUsersWithoutMetadataUseCase },
            lazy { refreshConversationsWithoutMetadataUseCase },
            lazy { timestampKeyRepository },
            TestKaliumDispatcher
        )
    }
}
