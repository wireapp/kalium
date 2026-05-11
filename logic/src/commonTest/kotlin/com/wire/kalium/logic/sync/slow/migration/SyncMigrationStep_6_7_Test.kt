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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.AccountRepository
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.sync.slow.migration.steps.SyncMigrationStep_6_7
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.accountRepository.updateSelfUserAvailabilityStatus(eq(UserAvailabilityStatus.NONE))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.selfTeamIdProvider.invoke()
        }
    }

    @Test
    fun givenUserIsATeamMember_whenIInvokingMigrationUser_thenAvailabilityStatusIsNotUpdated() = runTest {
        val (arrangement, migration) = Arrangement().arrange {
            withTeamId(Either.Right(TeamId("teamId")))
            withUpdateSelfUserAvailabilityStatus(Either.Right(Unit))
        }

        migration.invoke().shouldSucceed()

        verifySuspend(VerifyMode.not) {
            arrangement.accountRepository.updateSelfUserAvailabilityStatus(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.selfTeamIdProvider.invoke()
        }
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

        verifySuspend(VerifyMode.not) {
            arrangement.accountRepository.updateSelfUserAvailabilityStatus(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.selfTeamIdProvider.invoke()
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.accountRepository.updateSelfUserAvailabilityStatus(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.selfTeamIdProvider.invoke()
        }
    }

    private class Arrangement {
        val accountRepository = mock<AccountRepository>()
        val selfTeamIdProvider = mock<SelfTeamIdProvider>()

        private val migration: SyncMigrationStep_6_7 = SyncMigrationStep_6_7(
            lazy { accountRepository },
            selfTeamIdProvider
        )

        suspend fun withTeamId(teamId: Either<StorageFailure, TeamId?>) {
            everySuspend {
                selfTeamIdProvider.invoke()
            } returns teamId
        }

        suspend fun withUpdateSelfUserAvailabilityStatus(result: Either<StorageFailure, Unit>) {
            everySuspend {
                accountRepository.updateSelfUserAvailabilityStatus(any())
            } returns result
        }

        suspend fun arrange(block: suspend Arrangement.() -> Unit) = let {
            block()
            this to migration
        }
    }
}
