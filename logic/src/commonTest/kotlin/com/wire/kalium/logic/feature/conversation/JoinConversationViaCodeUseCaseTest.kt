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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberAddedResponse
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationCodeInfo
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class JoinConversationViaCodeUseCaseTest {

    @Test
    fun givenConversationJoined_thenReturnSuccess() = runTest {
        val code = "code"
        val key = "key"
        val domain = "domain"
        val password: String? = null

        val (useCae, arrangement) = Arrangement()
            .withJoinViaInviteCodeReturns(
                code,
                key,
                null,
                password,
                Either.Right(TestConversation.ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE)
            )
            .arrange()

        useCae(code, key, domain, password).also {
            assertIs<JoinConversationViaCodeUseCase.Result.Success.Changed>(it)
            assertEquals(
                TestConversation.ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE.event.qualifiedConversation.toModel(),
                it.conversationId
            )
        }

        coVerify {
            arrangement.conversationGroupRepository.joinViaInviteCode(any(), any(), eq<String?>(null), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationGroupRepository.fetchLimitedInfoViaInviteCode(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenConversationUnchanged_thenFetchConversationIdAndReturnUnchanged() = runTest {
        val code = "code"
        val key = "key"
        val domain = "domain"
        val password: String? = null

        val limitedConversationInfo = ConversationCodeInfo("id", null)
        val (useCae, arrangement) = Arrangement()
            .withJoinViaInviteCodeReturns(
                code,
                key,
                null,
                password,
                Either.Right(ConversationMemberAddedResponse.Unchanged)
            )
            .withFetchLimitedInfoViaInviteCodeReturns(code, key, Either.Right(limitedConversationInfo))
            .arrange()

        useCae(code, key, domain, password).also {
            assertIs<JoinConversationViaCodeUseCase.Result.Success.Unchanged>(it)
            assertEquals(ConversationId(limitedConversationInfo.nonQualifiedId, domain), it.conversationId)
        }

        coVerify {
            arrangement.conversationGroupRepository.joinViaInviteCode(any(), any(), eq<String?>(null), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationGroupRepository.fetchLimitedInfoViaInviteCode(any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenConversationUnchanged_whenNoDomainIsPassed_thenUseSelfUserDomain() = runTest {
        val code = "code"
        val key = "key"
        val domain = null
        val password: String? = null

        val limitedConversationInfo = ConversationCodeInfo("id", null)
        val (useCae, arrangement) = Arrangement()
            .withJoinViaInviteCodeReturns(
                code,
                key,
                null,
                password,
                Either.Right(ConversationMemberAddedResponse.Unchanged)
            )
            .withFetchLimitedInfoViaInviteCodeReturns(code, key, Either.Right(limitedConversationInfo))
            .arrange()

        useCae(code, key, domain, password).also {
            assertIs<JoinConversationViaCodeUseCase.Result.Success.Unchanged>(it)
            assertEquals(ConversationId(limitedConversationInfo.nonQualifiedId, selfUserId.domain), it.conversationId)
        }

        coVerify {
            arrangement.conversationGroupRepository.joinViaInviteCode(any(), any(), eq<String?>(null), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationGroupRepository.fetchLimitedInfoViaInviteCode(any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenFetchConversationCodeInfoFail_whenJoiningViaCode_thenReturnUnchangedWithNullConversationId() = runTest {
        val code = "code"
        val key = "key"
        val domain = null
        val password: String? = null

        val (useCae, arrangement) = Arrangement()
            .withJoinViaInviteCodeReturns(
                code,
                key,
                null,
                password,
                Either.Right(ConversationMemberAddedResponse.Unchanged)
            )
            .withFetchLimitedInfoViaInviteCodeReturns(
                code,
                key,
                Either.Left(NetworkFailure.NoNetworkConnection(CancellationException()))
            )
            .arrange()

        useCae(code, key, domain, password).also {
            assertIs<JoinConversationViaCodeUseCase.Result.Success.Unchanged>(it)
            assertEquals(null, it.conversationId)
        }

        coVerify {
            arrangement.conversationGroupRepository.joinViaInviteCode(any(), any(), eq<String?>(null), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationGroupRepository.fetchLimitedInfoViaInviteCode(any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenWrongPasswordError_whenJoiningViaCode_thenWrongPasswordIsReturned() = runTest {
        val code = "code"
        val key = "key"
        val domain = null
        val password: String? = null

        val (useCae, arrangement) = Arrangement()
            .withJoinViaInviteCodeReturns(
                code, key, null, password, Either.Left(
                    NetworkFailure.ServerMiscommunication(
                        KaliumException.InvalidRequestError(
                            ErrorResponse(403, "wrong password", "invalid-conversation-password")
                        )
                    )
                )
            )
            .arrange()

        useCae(code, key, domain, password).also {
            assertIs<JoinConversationViaCodeUseCase.Result.Failure.IncorrectPassword>(it)
        }

        coVerify {
            arrangement.conversationGroupRepository.joinViaInviteCode(any(), any(), eq<String?>(null), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationGroupRepository.fetchLimitedInfoViaInviteCode(any(), any())
        }.wasNotInvoked()
    }

    private companion object {
        val selfUserId = UserId("selfUserId", "selfUserIdDomain")
    }

    private class Arrangement {
        val conversationGroupRepository = mock(ConversationGroupRepository::class)
        private val useCase: JoinConversationViaCodeUseCase =
            JoinConversationViaCodeUseCase(conversationGroupRepository, selfUserId)

        suspend fun withJoinViaInviteCodeReturns(
            code: String,
            key: String,
            uri: String?,
            password: String?,
            result: Either<NetworkFailure, ConversationMemberAddedResponse>
        ): Arrangement = apply {
            coEvery {
                conversationGroupRepository.joinViaInviteCode(code, key, uri, password)
            }.returns(result)
        }

        suspend fun withFetchLimitedInfoViaInviteCodeReturns(
            code: String,
            key: String,
            result: Either<NetworkFailure, ConversationCodeInfo>
        ): Arrangement = apply {
            coEvery {
                conversationGroupRepository.fetchLimitedInfoViaInviteCode(code, key)
            }.returns(result)
        }

        fun arrange() = useCase to this
    }
}
