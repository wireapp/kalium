package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.AccountInfo
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NeedsToRegisterClientUseCaseTest {

    @Test
    fun givenAccountIsInvalid_thenReturnFalse() = runTest {

        val (arrangement, needsToRegisterClient) = Arrangement()
            .withUserAccountInfo(Either.Right(AccountInfo.Invalid(selfUserId, LogoutReason.SESSION_EXPIRED)))
            .arrange()
        needsToRegisterClient().also {
            assertEquals(false, it)
        }

        verify(arrangement.sessionRepository)
            .suspendFunction(arrangement.sessionRepository::userAccountInfo)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::currentClientId)
            .wasNotInvoked()
    }

    @Test
    fun givenAccountIsValidAndThereISNoClient_thenReturnTrue() = runTest {

        val (arrangement, needsToRegisterClient) = Arrangement()
            .withUserAccountInfo(Either.Right(AccountInfo.Valid(selfUserId)))
            .withCurrentClientId(Either.Left(StorageFailure.DataNotFound))
            .arrange()
        needsToRegisterClient().also {
            assertEquals(true, it)
        }

        verify(arrangement.sessionRepository)
            .suspendFunction(arrangement.sessionRepository::userAccountInfo)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::currentClientId)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAccountIsValidAndClientIsRegistered_thenReturnFalse() = runTest {

        val (arrangement, needsToRegisterClient) = Arrangement()
            .withUserAccountInfo(Either.Right(AccountInfo.Valid(selfUserId)))
            .withCurrentClientId(Either.Right(ClientId("client-id")))
            .arrange()
        needsToRegisterClient().also {
            assertEquals(false, it)
        }

        verify(arrangement.sessionRepository)
            .suspendFunction(arrangement.sessionRepository::userAccountInfo)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::currentClientId)
            .wasInvoked(exactly = once)
    }

    private companion object {
        val selfUserId = UserId("selfUserId", "selfUserDomain")
    }

    private class Arrangement {
        @Mock
        val clientRepository = configure(mock(classOf<ClientRepository>())) {
            stubsUnitByDefault = true
        }

        @Mock
        val sessionRepository = mock(SessionRepository::class)


        private var needsToRegisterClientUseCase: NeedsToRegisterClientUseCase =
            NeedsToRegisterClientUseCaseImpl(clientRepository, sessionRepository, selfUserId)


        fun withCurrentClientId(result: Either<StorageFailure, ClientId>) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::currentClientId)
                .whenInvoked()
                .then { result }
        }

        suspend fun withUserAccountInfo(result: Either<StorageFailure, AccountInfo>) = apply {
            given(sessionRepository)
                .coroutine { userAccountInfo(selfUserId) }
                .then { result }
        }

        fun arrange() = this to needsToRegisterClientUseCase
    }
}
