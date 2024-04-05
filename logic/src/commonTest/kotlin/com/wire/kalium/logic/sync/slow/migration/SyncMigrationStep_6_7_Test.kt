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
package com.wire.kalium.logic.sync.slow.migration

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.slow.migration.steps.SyncMigrationStep_6_7
import com.wire.kalium.logic.util.arrangement.provider.SelfTeamIdProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.SelfTeamIdProviderArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.AccountRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.AccountRepositoryArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.any
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

@Suppress("className")
class SyncMigrationStep_6_7_Test {

    @Test
    fun givenUserIsNotATeamMember_whenIInvokingMigrationUser_thenAvailabilityStatusIsUpdated() = runTest {
        val (arrangement, migration) = Arrangement().arrange {
            withTeamId(Either.Right(null))
            withUpdateSelfUserAvailabilityStatus(Either.Right(Unit))
        }

        migration.invoke().shouldSucceed()

        coVerify {
            arrangement.accountRepository.updateSelfUserAvailabilityStatus(eq(UserAvailabilityStatus.NONE))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.selfTeamIdProvider.invoke()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUserIsATeamMember_whenIInvokingMigrationUser_thenAvailabilityStatusIsNotUpdated() = runTest {
        val (arrangement, migration) = Arrangement().arrange {
            withTeamId(Either.Right(TeamId("teamId")))
            withUpdateSelfUserAvailabilityStatus(Either.Right(Unit))
        }

        migration.invoke().shouldSucceed()

        coVerify {
            arrangement.accountRepository.updateSelfUserAvailabilityStatus(any())
        }.wasNotInvoked()

        coVerify {
            arrangement.selfTeamIdProvider.invoke()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenError_whenGettingUserTeamId_thenReturnError() = runTest {
        val (arrangement, migration) = Arrangement().arrange {
            withTeamId(Either.Left(StorageFailure.DataNotFound))
            withUpdateSelfUserAvailabilityStatus(Either.Right(Unit))
        }

        migration.invoke().shouldFail {
            assertIs<StorageFailure.DataNotFound>(it)
        }

        coVerify {
            arrangement.accountRepository.updateSelfUserAvailabilityStatus(any())
        }.wasNotInvoked()

        coVerify {
            arrangement.selfTeamIdProvider.invoke()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenError_whenUpdatingUserStatus_thenReturnError() = runTest {
        val (arrangement, migration) = Arrangement().arrange {
            withTeamId(Either.Right(null))
            withUpdateSelfUserAvailabilityStatus(Either.Left(StorageFailure.DataNotFound))
        }

        migration.invoke().shouldFail {
            assertIs<StorageFailure.DataNotFound>(it)
        }

        coVerify {
            arrangement.accountRepository.updateSelfUserAvailabilityStatus(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.selfTeamIdProvider.invoke()
        }.wasInvoked(exactly = once)
    }

    private class Arrangement : AccountRepositoryArrangement by AccountRepositoryArrangementImpl(),
        SelfTeamIdProviderArrangement by SelfTeamIdProviderArrangementImpl() {

        private val migration: SyncMigrationStep_6_7 = SyncMigrationStep_6_7(
            lazy { accountRepository },
            selfTeamIdProvider
        )

        fun arrange(block: suspend Arrangement.() -> Unit) = let {
            runBlocking { block() }
            this to migration
        }
    }
}
