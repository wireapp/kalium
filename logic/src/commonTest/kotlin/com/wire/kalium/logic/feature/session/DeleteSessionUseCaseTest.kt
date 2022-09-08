package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.functional.Either
import io.ktor.utils.io.errors.IOException
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DeleteSessionUseCaseTest {

    @Test
    fun givenSuccess_WhenDeletingSessionLocally_thenSuccessAndResourcesAreFreed() {

        val userId = UserId("userId", "domain")
        val (arrange, deleteSessionUseCase) = Arrangement()
            .withSessionDeleteSuccess(userId)
            .arrange()

        deleteSessionUseCase(userId).also { result ->
            assertEquals(DeleteSessionUseCase.Result.Success, result)
        }

        verify(arrange.sessionRepository)
            .function(arrange.sessionRepository::deleteSession)
            .with(eq(userId))
            .wasInvoked(exactly = once)

        verify(arrange.userSessionScopeProvider)
            .function(arrange.userSessionScopeProvider::delete)
            .with(eq(userId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenFailure_WhenDeletingSessionLocally_thenReturnFailureAndResourcesAreNotFreed() {

        val userId = UserId("userId", "domain")
        val error = StorageFailure.Generic(IOException("Failed to delete session"))
        val (arrange, deleteSessionUseCase) = Arrangement()
            .withSessionDeleteFailure(userId, error)
            .arrange()

        deleteSessionUseCase(userId).also { result ->
            assertIs<DeleteSessionUseCase.Result.Failure>(result)
            assertEquals(error, result.cause)
        }

        verify(arrange.sessionRepository)
            .function(arrange.sessionRepository::deleteSession)
            .with(any())
            .wasNotInvoked()

        verify(arrange.userSessionScopeProvider)
            .function(arrange.userSessionScopeProvider::delete)
            .with(any())
            .wasNotInvoked()
    }

    private class Arrangement {
        @Mock
        val sessionRepository: SessionRepository = mock(classOf<SessionRepository>())

        @Mock
        val userSessionScopeProvider: UserSessionScopeProvider = mock(classOf<UserSessionScopeProvider>())

        val deleteSessionUseCase = DeleteSessionUseCase(sessionRepository, userSessionScopeProvider)

        fun withSessionDeleteSuccess(userId: UserId): Arrangement = apply {
            given(sessionRepository)
                .function(sessionRepository::deleteSession)
                .whenInvokedWith(eq(userId))
                .thenReturn(Either.Right(Unit))

            given(userSessionScopeProvider)
                .function(userSessionScopeProvider::delete)
                .whenInvokedWith(eq(userId))
                .thenReturn(Unit)
            return this
        }

        fun withSessionDeleteFailure(userId: UserId, error: StorageFailure): Arrangement = apply {
            given(sessionRepository)
                .function(sessionRepository::deleteSession)
                .whenInvokedWith(eq(userId))
                .thenReturn(Either.Left(error))

            return this
        }

        fun arrange() = this to deleteSessionUseCase
    }
}
