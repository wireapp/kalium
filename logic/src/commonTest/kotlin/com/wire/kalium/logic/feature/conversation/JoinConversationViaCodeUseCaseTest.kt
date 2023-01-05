package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberAddedResponse
import com.wire.kalium.network.api.base.authenticated.conversation.model.LimitedConversationInfo
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class JoinConversationViaCodeUseCaseTest {

    @Test
    fun givenConversationJoined_thenReturnSuccess() = runTest {
        val code = "code"
        val key = "key"
        val domain = "domain"

        val (useCae, arrangement) = Arrangement()
            .withJoinViaInviteCodeReturns(code, key, null, Either.Right(TestConversation.ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE))
            .arrange()

        useCae(code, key, domain).also {
            assertIs<JoinConversationViaCodeUseCase.Result.Success.Changed>(it)
            assertEquals(
                TestConversation.ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE.event.qualifiedConversation.toModel(),
                it.conversationId
            )
        }

        verify(arrangement.conversationGroupRepository)
            .suspendFunction(arrangement.conversationGroupRepository::joinViaInviteCode)
            .with(any(), any(), eq(null))
            .wasInvoked(exactly = once)

        verify(arrangement.conversationGroupRepository)
            .suspendFunction(arrangement.conversationGroupRepository::fetchLimitedInfoViaInviteCode)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenConversationUnchanged_thenFetchConversationIdAndReturnUnchanged() = runTest {
        val code = "code"
        val key = "key"
        val domain = "domain"

        val limitedConversationInfo = LimitedConversationInfo("id", null)
        val (useCae, arrangement) = Arrangement()
            .withJoinViaInviteCodeReturns(code, key, null, Either.Right(ConversationMemberAddedResponse.Unchanged))
            .withFetchLimitedInfoViaInviteCodeReturns(code, key, Either.Right(limitedConversationInfo))
            .arrange()

        useCae(code, key, domain).also {
            assertIs<JoinConversationViaCodeUseCase.Result.Success.Unchanged>(it)
            assertEquals(ConversationId(limitedConversationInfo.nonQualifiedConversationId, domain), it.conversationId)
        }

        verify(arrangement.conversationGroupRepository)
            .suspendFunction(arrangement.conversationGroupRepository::joinViaInviteCode)
            .with(any(), any(), eq(null))
            .wasInvoked(exactly = once)

        verify(arrangement.conversationGroupRepository)
            .suspendFunction(arrangement.conversationGroupRepository::fetchLimitedInfoViaInviteCode)
            .with(any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenConversationUnchanged_whenNoDomainIsPassed_thenUseSelfUserDomain() = runTest {
        val code = "code"
        val key = "key"
        val domain = null

        val limitedConversationInfo = LimitedConversationInfo("id", null)
        val (useCae, arrangement) = Arrangement()
            .withJoinViaInviteCodeReturns(code, key, null, Either.Right(ConversationMemberAddedResponse.Unchanged))
            .withFetchLimitedInfoViaInviteCodeReturns(code, key, Either.Right(limitedConversationInfo))
            .arrange()

        useCae(code, key, domain).also {
            assertIs<JoinConversationViaCodeUseCase.Result.Success.Unchanged>(it)
            assertEquals(ConversationId(limitedConversationInfo.nonQualifiedConversationId, selfUserId.domain), it.conversationId)
        }

        verify(arrangement.conversationGroupRepository)
            .suspendFunction(arrangement.conversationGroupRepository::joinViaInviteCode)
            .with(any(), any(), eq(null))
            .wasInvoked(exactly = once)

        verify(arrangement.conversationGroupRepository)
            .suspendFunction(arrangement.conversationGroupRepository::fetchLimitedInfoViaInviteCode)
            .with(any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenFetchLimitedConversationInfoFail_whenJoiningViaCode_thenReturnUnchangedWithNullConversationId() = runTest {
        val code = "code"
        val key = "key"
        val domain = null

        val (useCae, arrangement) = Arrangement()
            .withJoinViaInviteCodeReturns(code, key, null, Either.Right(ConversationMemberAddedResponse.Unchanged))
            .withFetchLimitedInfoViaInviteCodeReturns(code, key, Either.Left(NetworkFailure.NoNetworkConnection(IOException())))
            .arrange()

        useCae(code, key, domain).also {
            assertIs<JoinConversationViaCodeUseCase.Result.Success.Unchanged>(it)
            assertEquals(null, it.conversationId)
        }

        verify(arrangement.conversationGroupRepository)
            .suspendFunction(arrangement.conversationGroupRepository::joinViaInviteCode)
            .with(any(), any(), eq(null))
            .wasInvoked(exactly = once)

        verify(arrangement.conversationGroupRepository)
            .suspendFunction(arrangement.conversationGroupRepository::fetchLimitedInfoViaInviteCode)
            .with(any(), any())
            .wasInvoked(exactly = once)
    }

    private companion object {
        val selfUserId = UserId("selfUserId", "selfUserIdDomain")
    }

    private class Arrangement {
        val conversationGroupRepository = mock(ConversationGroupRepository::class)
        private val useCase: JoinConversationViaCodeUseCase = JoinConversationViaCodeUseCase(conversationGroupRepository, selfUserId)

        suspend fun withJoinViaInviteCodeReturns(
            code: String,
            key: String,
            uri: String?,
            result: Either<CoreFailure, ConversationMemberAddedResponse>
        ): Arrangement = apply {
            given(conversationGroupRepository)
                .coroutine { conversationGroupRepository.joinViaInviteCode(code, key, uri) }
                .thenReturn(result)
        }

        suspend fun withFetchLimitedInfoViaInviteCodeReturns(
            code: String,
            key: String,
            result: Either<NetworkFailure, LimitedConversationInfo>
        ): Arrangement = apply {
            given(conversationGroupRepository)
                .coroutine { conversationGroupRepository.fetchLimitedInfoViaInviteCode(code, key) }
                .thenReturn(result)
        }

        fun arrange() = useCase to this
    }
}
