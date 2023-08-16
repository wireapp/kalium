/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
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

            verify(arrangement.refreshConversationsWithoutMetadataUseCase)
                .suspendFunction(arrangement.refreshConversationsWithoutMetadataUseCase::invoke)
                .wasInvoked(once)

            verify(arrangement.refreshUsersWithoutMetadataUseCase)
                .suspendFunction(arrangement.refreshUsersWithoutMetadataUseCase::invoke)
                .wasInvoked(once)

            verify(arrangement.timestampKeyRepository)
                .suspendFunction(arrangement.timestampKeyRepository::reset)
                .with(anything())
                .wasInvoked(once)
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

            verify(arrangement.refreshConversationsWithoutMetadataUseCase)
                .suspendFunction(arrangement.refreshConversationsWithoutMetadataUseCase::invoke)
                .wasNotInvoked()

            verify(arrangement.refreshUsersWithoutMetadataUseCase)
                .suspendFunction(arrangement.refreshUsersWithoutMetadataUseCase::invoke)
                .wasNotInvoked()

            verify(arrangement.timestampKeyRepository)
                .suspendFunction(arrangement.timestampKeyRepository::reset)
                .with(anything())
                .wasNotInvoked()
        }

    private class Arrangement {

        val incrementalSyncRepository: IncrementalSyncRepository = InMemoryIncrementalSyncRepository()

        @Mock
        val timestampKeyRepository = mock(classOf<TimestampKeyRepository>())

        @Mock
        val refreshConversationsWithoutMetadataUseCase = mock(classOf<RefreshConversationsWithoutMetadataUseCase>())

        @Mock
        val refreshUsersWithoutMetadataUseCase = mock(classOf<RefreshUsersWithoutMetadataUseCase>())

        fun withLastMetadataSyncKeyCheck(hasPassed: Boolean) = apply {
            given(timestampKeyRepository)
                .suspendFunction(timestampKeyRepository::hasPassed)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(hasPassed))
        }

        fun withLastMetadataSyncKeyResetCheckSuccessful() = apply {
            given(timestampKeyRepository)
                .suspendFunction(timestampKeyRepository::reset)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withRefreshUsersSuccess() = apply {
            given(refreshUsersWithoutMetadataUseCase)
                .suspendFunction(refreshUsersWithoutMetadataUseCase::invoke)
                .whenInvoked()
                .thenReturn(Unit)
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
