package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.api.base.authenticated.conversation.model.LimitedConversationInfo
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CheckConversationInviteCodeUseCaseTest {

    @Test
    fun givenSuccess_whenFetchingConversationInfoViaCode_thenReturnSuccess() = runTest {
        val conversationInfo = LimitedConversationInfo("id", null)
        val (code, key, domain) = Triple("code", "key", "domain")

        val (arrangement, useCase) = Arrangement()
            .withFetchLimitedInfoViaInviteCodeResult(code, key, Either.Right(conversationInfo))
            .withObserveIsUserMemberResult(
                ConversationId(conversationInfo.nonQualifiedConversationId, domain),
                selfUserId,
                Either.Right(true)
            )
            .arrange()

        useCase(code, key, domain).also { result ->
            assertIs<CheckConversationInviteCodeUseCase.Result.Success>(result)
            assertEquals(conversationInfo.name, result.name)
            assertEquals(result.conversationId, ConversationId(conversationInfo.nonQualifiedConversationId, domain))
            assertTrue(result.isSelfMember)
        }

        with(arrangement) {
            verify(conversationGroupRepository)
                .suspendFunction(conversationGroupRepository::fetchLimitedInfoViaInviteCode)
                .with(any(), any())
                .wasInvoked(exactly = once)

            verify(conversationRepository)
                .suspendFunction(conversationRepository::observeIsUserMember)
                .with(any(), any())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun whenNoDomainIsPassed_thenUseTheUserSelfDomain() = runTest {
        val conversationInfo = LimitedConversationInfo("id", null)
        val (code, key, domain) = Triple("code", "key", null)

        val (arrangement, useCase) = Arrangement()
            .withFetchLimitedInfoViaInviteCodeResult(code, key, Either.Right(conversationInfo))
            .withObserveIsUserMemberResult(
                ConversationId(conversationInfo.nonQualifiedConversationId, selfUserId.domain),
                selfUserId,
                Either.Right(true)
            )
            .arrange()

        useCase(code, key, domain).also { result ->
            assertIs<CheckConversationInviteCodeUseCase.Result.Success>(result)
            assertEquals(conversationInfo.name, result.name)
            assertEquals(result.conversationId, ConversationId(conversationInfo.nonQualifiedConversationId, selfUserId.domain))
            assertTrue(result.isSelfMember)
        }

        with(arrangement) {
            verify(conversationGroupRepository)
                .suspendFunction(conversationGroupRepository::fetchLimitedInfoViaInviteCode)
                .with(any(), any())
                .wasInvoked(exactly = once)

            verify(conversationRepository)
                .suspendFunction(conversationRepository::observeIsUserMember)
                .with(any(), any())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenErrorCode400_whenFetchingConversationInfoViaCode_thenInvalidCodeError() = runTest {
        val (code, key, domain) = Triple("code", "key", "domain")

        val (arrangement, useCase) = Arrangement()
            .withFetchLimitedInfoViaInviteCodeResult(
                code,
                key,
                Either.Left(NetworkFailure.ServerMiscommunication(TestNetworkException.badRequest))
            )
            .arrange()

        useCase(code, key, domain).also { result ->
            assertIs<CheckConversationInviteCodeUseCase.Result.Failure.InvalidCodeOrKey>(result)
        }

        with(arrangement) {
            verify(conversationGroupRepository)
                .suspendFunction(conversationGroupRepository::fetchLimitedInfoViaInviteCode)
                .with(any(), any())
                .wasInvoked(exactly = once)

            verify(conversationRepository)
                .suspendFunction(conversationRepository::observeIsUserMember)
                .with(any(), any())
                .wasNotInvoked()
        }
    }

    @Test
    fun givenErrorNoTeamMember_whenFetchingConversationInfoViaCode_thenRequestingUserIsNotATeamMemberIsReturned() = runTest {
        val (code, key, domain) = Triple("code", "key", "domain")

        val (arrangement, useCase) = Arrangement()
            .withFetchLimitedInfoViaInviteCodeResult(
                code,
                key,
                Either.Left(NetworkFailure.ServerMiscommunication(TestNetworkException.noTeamMember))
            )
            .arrange()

        useCase(code, key, domain).also { result ->
            assertIs<CheckConversationInviteCodeUseCase.Result.Failure.RequestingUserIsNotATeamMember>(result)
        }

        with(arrangement) {
            verify(conversationGroupRepository)
                .suspendFunction(conversationGroupRepository::fetchLimitedInfoViaInviteCode)
                .with(any(), any())
                .wasInvoked(exactly = once)

            verify(conversationRepository)
                .suspendFunction(conversationRepository::observeIsUserMember)
                .with(any(), any())
                .wasNotInvoked()
        }
    }

    @Test
    fun givenErrorNoConversation_whenFetchingConversationInfoViaCode_thenConversationNotFoundIsReturned() = runTest {
        val (code, key, domain) = Triple("code", "key", "domain")

        val (arrangement, useCase) = Arrangement()
            .withFetchLimitedInfoViaInviteCodeResult(
                code,
                key,
                Either.Left(NetworkFailure.ServerMiscommunication(TestNetworkException.noConversation))
            )
            .arrange()

        useCase(code, key, domain).also { result ->
            assertIs<CheckConversationInviteCodeUseCase.Result.Failure.ConversationNotFound>(result)
        }

        with(arrangement) {
            verify(conversationGroupRepository)
                .suspendFunction(conversationGroupRepository::fetchLimitedInfoViaInviteCode)
                .with(any(), any())
                .wasInvoked(exactly = once)

            verify(conversationRepository)
                .suspendFunction(conversationRepository::observeIsUserMember)
                .with(any(), any())
                .wasNotInvoked()
        }
    }

    @Test
    fun givenErrorNoConversationCode_whenFetchingConversationInfoViaCode_thenConversationNotFoundIsReturned() = runTest {
        val (code, key, domain) = Triple("code", "key", "domain")

        val (arrangement, useCase) = Arrangement()
            .withFetchLimitedInfoViaInviteCodeResult(
                code,
                key,
                Either.Left(NetworkFailure.ServerMiscommunication(TestNetworkException.noConversationCode))
            )
            .arrange()

        useCase(code, key, domain).also { result ->
            assertIs<CheckConversationInviteCodeUseCase.Result.Failure.ConversationNotFound>(result)
        }

        with(arrangement) {
            verify(conversationGroupRepository)
                .suspendFunction(conversationGroupRepository::fetchLimitedInfoViaInviteCode)
                .with(any(), any())
                .wasInvoked(exactly = once)

            verify(conversationRepository)
                .suspendFunction(conversationRepository::observeIsUserMember)
                .with(any(), any())
                .wasNotInvoked()
        }
    }

    @Test
    fun givenGuestLinkDisabled_whenFetchingConversationInfoViaCode_thenGuestLinksDisabledIsReturned() = runTest {
        val (code, key, domain) = Triple("code", "key", "domain")

        val (arrangement, useCase) = Arrangement()
            .withFetchLimitedInfoViaInviteCodeResult(
                code,
                key,
                Either.Left(NetworkFailure.ServerMiscommunication(TestNetworkException.guestLinkDisables))
            )
            .arrange()

        useCase(code, key, domain).also { result ->
            assertIs<CheckConversationInviteCodeUseCase.Result.Failure.GuestLinksDisabled>(result)
        }

        with(arrangement) {
            verify(conversationGroupRepository)
                .suspendFunction(conversationGroupRepository::fetchLimitedInfoViaInviteCode)
                .with(any(), any())
                .wasInvoked(exactly = once)

            verify(conversationRepository)
                .suspendFunction(conversationRepository::observeIsUserMember)
                .with(any(), any())
                .wasNotInvoked()
        }
    }

    private companion object {
        val selfUserId = UserId("self-user-id", "self-user-domain")
    }

    private class Arrangement {
        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        @Mock
        val conversationGroupRepository = mock(ConversationGroupRepository::class)

        private val useCase = CheckConversationInviteCodeUseCase(
            conversationGroupRepository = conversationGroupRepository,
            conversationRepository = conversationRepository,
            selfUserId = selfUserId
        )

        fun withFetchLimitedInfoViaInviteCodeResult(
            code: String,
            key: String,
            result: Either<NetworkFailure, LimitedConversationInfo>
        ) = apply {
            given(conversationGroupRepository)
                .suspendFunction(conversationGroupRepository::fetchLimitedInfoViaInviteCode)
                .whenInvokedWith(eq(code), eq(key))
                .thenReturn(result)
        }

        fun withObserveIsUserMemberResult(
            conversationId: ConversationId,
            userId: UserId,
            result: Either<CoreFailure, Boolean>
        ) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::observeIsUserMember)
                .whenInvokedWith(eq(conversationId), eq(userId))
                .thenReturn(flowOf(result))
        }

        fun arrange() = this to useCase
    }
}
