package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.model.SsoIdEntity
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
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

        verify(arrangement.sessionRepository)
            .invocation { arrangement.sessionRepository.ssoId(arrangement.selfUserId) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenUSerHasSsoIdAndSubjectIsNotNull_thenReturnTrue() = runTest {
        val (arrangement, isPasswordRequired) = Arrangement()
            .withSelfSsoId(Either.Right(SsoIdEntity(subject = "subject", scimExternalId = null, tenant = null)))
            .arrange()

        isPasswordRequired.eitherInvoke().shouldSucceed {
            assertTrue(it)
        }

        verify(arrangement.sessionRepository)
            .invocation { arrangement.sessionRepository.ssoId(arrangement.selfUserId) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenUSerHasSsoIdAndSubjectIsNull_thenReturnFalse() = runTest {
        val (arrangement, isPasswordRequired) = Arrangement()
            .withSelfSsoId(Either.Right(SsoIdEntity(subject = null, scimExternalId = null, tenant = null)))
            .arrange()

        isPasswordRequired.eitherInvoke().shouldSucceed {
            assertFalse(it)
        }

        verify(arrangement.sessionRepository)
            .invocation { arrangement.sessionRepository.ssoId(arrangement.selfUserId) }
            .wasInvoked(exactly = once)
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

        verify(arrangement.sessionRepository)
            .invocation { arrangement.sessionRepository.ssoId(arrangement.selfUserId) }
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val sessionRepository: SessionRepository = mock(SessionRepository::class)

        val selfUserId = UserId("user_id", "domain")

        val isPasswordRequired = IsPasswordRequiredUseCase(selfUserId, sessionRepository)

        fun withSelfSsoId(ssoId: Either<StorageFailure, SsoIdEntity?>) = apply {
            given(sessionRepository).function(sessionRepository::ssoId).whenInvokedWith(any()).then { ssoId }
        }

        fun arrange(): Pair<Arrangement, IsPasswordRequiredUseCase> = this to isPasswordRequired
    }
}
