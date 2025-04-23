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

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.model.SsoIdEntity
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class IsPasswordRequiredUseCaseTest {

    @Test
    fun givenUSerHasNoSsoId_thenReturnTrue() = runTest {
        val (arrangement, isPasswordRequired) = Arrangement()
            .withSelfSsoId(Either.Right(null))
            .arrange()

        isPasswordRequired.eitherInvoke().shouldSucceed {
            assertTrue(it)
        }

        coVerify {
            arrangement.sessionRepository.ssoId(arrangement.selfUserId)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUSerHasSsoIdAndSubjectIsNotNull_thenReturnTrue() = runTest {
        val (arrangement, isPasswordRequired) = Arrangement()
            .withSelfSsoId(Either.Right(SsoIdEntity(subject = "subject", scimExternalId = null, tenant = null)))
            .arrange()

        isPasswordRequired.eitherInvoke().shouldSucceed {
            assertFalse(it)
        }

        coVerify {
            arrangement.sessionRepository.ssoId(arrangement.selfUserId)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUSerHasSsoIdAndSubjectIsNull_thenReturnFalse() = runTest {
        val (arrangement, isPasswordRequired) = Arrangement()
            .withSelfSsoId(Either.Right(SsoIdEntity(subject = null, scimExternalId = null, tenant = null)))
            .arrange()

        isPasswordRequired.eitherInvoke().shouldSucceed {
            assertTrue(it)
        }

        coVerify {
            arrangement.sessionRepository.ssoId(arrangement.selfUserId)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenStorageError_whenGettingUserSSOId_thenReturnErrorIsPropagated() = runTest {
        val (arrangement, isPasswordRequired) = Arrangement()
            .withSelfSsoId(Either.Left(StorageFailure.Generic(IOException())))
            .arrange()

        isPasswordRequired.eitherInvoke().shouldFail {
            assertIs<StorageFailure.Generic>(it)
            assertIs<IOException>(it.rootCause)

        }

        coVerify {
            arrangement.sessionRepository.ssoId(arrangement.selfUserId)
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        val sessionRepository: SessionRepository = mock(SessionRepository::class)

        val selfUserId = UserId("user_id", "domain")

        val isPasswordRequired = IsPasswordRequiredUseCase(selfUserId, sessionRepository)

        suspend fun withSelfSsoId(ssoId: Either<StorageFailure, SsoIdEntity?>) = apply {
            coEvery {
                sessionRepository.ssoId(any())
            }.returns(ssoId)
        }

        fun arrange(): Pair<Arrangement, IsPasswordRequiredUseCase> = this to isPasswordRequired
    }
}
