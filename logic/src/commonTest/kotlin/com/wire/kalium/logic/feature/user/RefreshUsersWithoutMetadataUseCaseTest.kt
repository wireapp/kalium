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

package com.wire.kalium.logic.feature.user

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCaseImpl
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@ExperimentalCoroutinesApi
class RefreshUsersWithoutMetadataUseCaseTest {

    @Test
    fun givenUsersWithoutMetadata_whenRefreshing_thenShouldRefreshThoseUsersInformation() = runTest {
        val (arrangement, refreshUsersWithoutMetadata) = Arrangement(testKaliumDispatcher)
            .withResponse()
            .arrange()

        refreshUsersWithoutMetadata()

        coVerify {
            arrangement.userRepository.syncUsersWithoutMetadata()
        }.wasInvoked(once)
    }

    private class Arrangement(private var dispatcher: KaliumDispatcher = TestKaliumDispatcher) {
                val userRepository = mock(UserRepository::class)

        suspend fun withResponse(result: Either<CoreFailure, Unit> = Either.Right(Unit)) = apply {
            coEvery {
                userRepository.syncUsersWithoutMetadata()
            }.returns(result)
        }

        fun arrange(): Pair<Arrangement, RefreshUsersWithoutMetadataUseCase> =
            this to RefreshUsersWithoutMetadataUseCaseImpl(userRepository, dispatcher)
    }
}
