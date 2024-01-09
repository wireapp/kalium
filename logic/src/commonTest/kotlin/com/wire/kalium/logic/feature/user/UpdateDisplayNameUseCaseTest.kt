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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.user.AccountRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateDisplayNameUseCaseTest {

    @Test
    fun givenValidParams_whenUpdatingDisplayName_thenShouldReturnASuccessResult() = runTest {
        val (arrangement, updateDisplayName) = Arrangement()
            .withSuccessfulUploadResponse()
            .arrange()

        val result = updateDisplayName(NEW_DISPLAY_NAME)

        assertTrue(result is DisplayNameUpdateResult.Success)
        verify(arrangement.accountRepository)
            .suspendFunction(arrangement.accountRepository::updateSelfDisplayName)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun givenAnError_whenUpdatingDisplayName_thenShouldReturnAMappedCoreFailure() = runTest {
        val (arrangement, updateDisplayName) = Arrangement()
            .withErrorResponse()
            .arrange()

        val result = updateDisplayName(NEW_DISPLAY_NAME)

        assertTrue(result is DisplayNameUpdateResult.Failure)
        verify(arrangement.accountRepository)
            .suspendFunction(arrangement.accountRepository::updateSelfDisplayName)
            .with(any())
            .wasInvoked(once)
    }

    private class Arrangement {

        @Mock
        val accountRepository = mock(classOf<AccountRepository>())

        fun withSuccessfulUploadResponse() = apply {
            given(accountRepository)
                .suspendFunction(accountRepository::updateSelfDisplayName)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withErrorResponse() = apply {
            given(accountRepository)
                .suspendFunction(accountRepository::updateSelfDisplayName)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(CoreFailure.Unknown(Throwable("an error"))))
        }

        fun arrange() = this to UpdateDisplayNameUseCaseImpl(accountRepository)
    }

    companion object {
        const val NEW_DISPLAY_NAME = "new display name"
    }
}
