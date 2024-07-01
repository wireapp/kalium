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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.api.authenticated.conversation.model.ConversationCodeInfo
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CheckConversationInviteCodeUseCaseTest {

    @Test
    fun givenSuccess_whenFetchingConversationInfoViaCode_thenReturnSuccess() = runTest {
        val conversationInfo = ConversationCodeInfo("id", null)
        val (code, key, domain) = Triple("code", "key", "domain")

        val (arrangement, useCase) = Arrangement()
            .withFetchLimitedInfoViaInviteCodeResult(code, key, Either.Right(conversationInfo))
            .withObserveIsUserMemberResult(
                ConversationId(conversationInfo.nonQualifiedId, domain),
                selfUserId,
                Either.Right(true)
            )
            .arrange()

        useCase(code, key, domain).also { result ->
            assertIs<CheckConversationInviteCodeUseCase.Result.Success>(result)
            assertEquals(conversationInfo.name, result.name)
            assertEquals(result.conversationId, ConversationId(conversationInfo.nonQualifiedId, domain))
            assertTrue(result.isSelfMember)
        }

        with(arrangement) {
            coVerify {
                conversationGroupRepository.fetchLimitedInfoViaInviteCode(any(), any())
            }.wasInvoked(exactly = once)

            coVerify {
                conversationRepository.observeIsUserMember(any(), any())
            }.wasInvoked(exactly = once)
        }
    }

    @Test
    fun whenNoDomainIsPassed_thenUseTheUserSelfDomain() = runTest {
        val conversationInfo = ConversationCodeInfo("id", null)
        val (code, key, domain) = Triple("code", "key", null)

        val (arrangement, useCase) = Arrangement()
            .withFetchLimitedInfoViaInviteCodeResult(code, key, Either.Right(conversationInfo))
            .withObserveIsUserMemberResult(
                ConversationId(conversationInfo.nonQualifiedId, selfUserId.domain),
                selfUserId,
                Either.Right(true)
            )
            .arrange()

        useCase(code, key, domain).also { result ->
            assertIs<CheckConversationInviteCodeUseCase.Result.Success>(result)
            assertEquals(conversationInfo.name, result.name)
            assertEquals(result.conversationId, ConversationId(conversationInfo.nonQualifiedId, selfUserId.domain))
            assertTrue(result.isSelfMember)
        }

        with(arrangement) {
            coVerify {
                conversationGroupRepository.fetchLimitedInfoViaInviteCode(any(), any())
            }.wasInvoked(exactly = once)

            coVerify {
                conversationRepository.observeIsUserMember(any(), any())
            }.wasInvoked(exactly = once)
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
            coVerify {
                conversationGroupRepository.fetchLimitedInfoViaInviteCode(any(), any())
            }.wasInvoked(exactly = once)

            coVerify {
                conversationRepository.observeIsUserMember(any(), any())
            }.wasNotInvoked()
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
            coVerify {
                conversationGroupRepository.fetchLimitedInfoViaInviteCode(any(), any())
            }.wasInvoked(exactly = once)

            coVerify {
                conversationRepository.observeIsUserMember(any(), any())
            }.wasNotInvoked()
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
            coVerify {
                conversationGroupRepository.fetchLimitedInfoViaInviteCode(any(), any())
            }.wasInvoked(exactly = once)

            coVerify {
                conversationRepository.observeIsUserMember(any(), any())
            }.wasNotInvoked()
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
            coVerify {
                conversationGroupRepository.fetchLimitedInfoViaInviteCode(any(), any())
            }.wasInvoked(exactly = once)

            coVerify {
                conversationRepository.observeIsUserMember(any(), any())
            }.wasNotInvoked()
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
            coVerify {
                conversationGroupRepository.fetchLimitedInfoViaInviteCode(any(), any())
            }.wasInvoked(exactly = once)

            coVerify {
                conversationRepository.observeIsUserMember(any(), any())
            }.wasNotInvoked()
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

        suspend fun withFetchLimitedInfoViaInviteCodeResult(
            code: String,
            key: String,
            result: Either<NetworkFailure, ConversationCodeInfo>
        ) = apply {
            coEvery {
                conversationGroupRepository.fetchLimitedInfoViaInviteCode(eq(code), eq(key))
            }.returns(result)
        }

        suspend fun withObserveIsUserMemberResult(
            conversationId: ConversationId,
            userId: UserId,
            result: Either<CoreFailure, Boolean>
        ) = apply {
            coEvery {
                conversationRepository.observeIsUserMember(eq(conversationId), eq(userId))
            }.returns(flowOf(result))
        }

        fun arrange() = this to useCase
    }
}
