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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.sync.receiver.conversation.MemberJoinEventHandler
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMockativeImpl
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberAddedResponse
import com.wire.kalium.network.api.authenticated.conversation.model.ConversationCodeInfo
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isWrongConversationPassword
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
    fun givenConversationJoinedWithProteusProtocol_thenReturnSuccessAndHandlerIsInvokedButNotMLSJoining() = runTest {
        val code = "code"
        val key = "key"
        val domain = "domain"
        val password: String? = null

        val (useCase, arrangement) = Arrangement()
            .withJoinViaInviteCodeReturns(
                code,
                key,
                null,
                password,
                Either.Right(TestConversation.ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE)
            )
            .withMemberJoinHandler()
            .withProtocolInfo(Either.Right(TestConversation.PROTEUS_PROTOCOL_INFO))
            .arrange()

        useCase(code, key, domain, password).also {
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
            arrangement.memberJoinEventHandler.handle(any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.joinExistingMLSConversation.invoke(any(), any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenConversationJoinedWithMLSProtocol_thenHandlerAndMLSJoiningAreInvoked() = runTest {
        val code = "code"
        val key = "key"
        val domain = "domain"
        val password: String? = null

        val (useCase, arrangement) = Arrangement()
            .withJoinViaInviteCodeReturns(
                code,
                key,
                null,
                password,
                Either.Right(TestConversation.ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE)
            )
            .withMemberJoinHandler()
            .withProtocolInfo(Either.Right(TestConversation.MLS_PROTOCOL_INFO))
            .withJoinExistingMLSConversationSucceeds()
            .withAddMemberToMLSGroupSucceeds()
            .arrange()

        useCase(code, key, domain, password).also {
            assertIs<JoinConversationViaCodeUseCase.Result.Success.Changed>(it)
        }

        coVerify {
            arrangement.memberJoinEventHandler.handle(any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.joinExistingMLSConversation.invoke(any(), any(), any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.mlsConversationRepository.addMemberToMLSGroup(any(), any(), eq(listOf(selfUserId)), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenConversationUnchanged_thenHandlerIsNotInvoked() = runTest {
        val code = "code"
        val key = "key"
        val domain = "domain"
        val password: String? = null

        val limitedConversationInfo = ConversationCodeInfo("id", null)
        val (useCase, arrangement) = Arrangement()
            .withJoinViaInviteCodeReturns(
                code,
                key,
                null,
                password,
                Either.Right(ConversationMemberAddedResponse.Unchanged)
            )
            .withFetchLimitedInfoViaInviteCodeReturns(code, key, Either.Right(limitedConversationInfo))
            .arrange()

        useCase(code, key, domain, password).also {
            assertIs<JoinConversationViaCodeUseCase.Result.Success.Unchanged>(it)
            assertEquals(ConversationId(limitedConversationInfo.nonQualifiedId, domain), it.conversationId)
        }

        coVerify {
            arrangement.conversationGroupRepository.joinViaInviteCode(any(), any(), eq<String?>(null), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationGroupRepository.fetchLimitedInfoViaInviteCode(any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.memberJoinEventHandler.handle(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenConversationUnchanged_whenNoDomainIsPassed_thenUseSelfUserDomain() = runTest {
        val code = "code"
        val key = "key"
        val domain = null
        val password: String? = null

        val limitedConversationInfo = ConversationCodeInfo("id", null)
        val (useCase, arrangement) = Arrangement()
            .withJoinViaInviteCodeReturns(
                code,
                key,
                null,
                password,
                Either.Right(ConversationMemberAddedResponse.Unchanged)
            )
            .withFetchLimitedInfoViaInviteCodeReturns(code, key, Either.Right(limitedConversationInfo))
            .arrange()

        useCase(code, key, domain, password).also {
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

        val (useCase, arrangement) = Arrangement()
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

        useCase(code, key, domain, password).also {
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
    fun givenMemberJoinHandlerFails_whenJoiningViaCode_thenReturnGenericFailure() = runTest {
        val code = "code"
        val key = "key"
        val domain = "domain"
        val password: String? = null
        val storageFailure = StorageFailure.Generic(RuntimeException("db error"))

        val (useCase, arrangement) = Arrangement()
            .withJoinViaInviteCodeReturns(
                code,
                key,
                null,
                password,
                Either.Right(TestConversation.ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE)
            )
            .withMemberJoinHandler(Either.Left(storageFailure))
            .arrange()

        useCase(code, key, domain, password).also {
            assertIs<JoinConversationViaCodeUseCase.Result.Failure.Generic>(it)
            assertEquals(storageFailure, it.failure)
        }

        coVerify {
            arrangement.memberJoinEventHandler.handle(any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.joinExistingMLSConversation.invoke(any(), any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenMLSJoinFails_whenJoiningViaCode_thenReturnGenericFailure() = runTest {
        val code = "code"
        val key = "key"
        val domain = "domain"
        val password: String? = null
        val mlsFailure = NetworkFailure.NoNetworkConnection(RuntimeException("no network"))

        val (useCase, arrangement) = Arrangement()
            .withJoinViaInviteCodeReturns(
                code,
                key,
                null,
                password,
                Either.Right(TestConversation.ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE)
            )
            .withMemberJoinHandler()
            .withProtocolInfo(Either.Right(TestConversation.MLS_PROTOCOL_INFO))
            .also { arr ->
                coEvery {
                    arr.joinExistingMLSConversation.invoke(any(), any(), any(), any())
                }.returns(Either.Left(mlsFailure))
            }
            .arrange()

        useCase(code, key, domain, password).also {
            assertIs<JoinConversationViaCodeUseCase.Result.Failure.Generic>(it)
            assertEquals(mlsFailure, it.failure)
        }

        coVerify {
            arrangement.mlsConversationRepository.addMemberToMLSGroup(any(), any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenWrongPasswordError_whenJoiningViaCode_thenWrongPasswordIsReturned() = runTest {
        val code = "code"
        val key = "key"
        val domain = null
        val password: String? = null

        val (useCase, arrangement) = Arrangement()
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

        useCase(code, key, domain, password).also {
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

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMockativeImpl() {
        val conversationGroupRepository = mock(ConversationGroupRepository::class)
        val conversationRepository = mock(ConversationRepository::class)
        val memberJoinEventHandler = mock(MemberJoinEventHandler::class)
        val joinExistingMLSConversation = mock(JoinExistingMLSConversationUseCase::class)
        val mlsConversationRepository = mock(MLSConversationRepository::class)
        private val useCase: JoinConversationViaCodeUseCase =
            JoinConversationViaCodeUseCase(
                conversationGroupRepository,
                conversationRepository,
                memberJoinEventHandler,
                joinExistingMLSConversation,
                mlsConversationRepository,
                cryptoTransactionProvider,
                selfUserId,
            )

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

        suspend fun withMemberJoinHandler(result: Either<CoreFailure, Unit> = Either.Right(Unit)): Arrangement = apply {
            coEvery {
                memberJoinEventHandler.handle(any(), any())
            }.returns(result)
            withTransactionReturning(result)
        }

        suspend fun withProtocolInfo(result: Either<StorageFailure, com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo>): Arrangement = apply {
            coEvery {
                conversationRepository.getConversationProtocolInfo(any())
            }.returns(result)
        }

        suspend fun withJoinExistingMLSConversationSucceeds(): Arrangement = apply {
            coEvery {
                joinExistingMLSConversation.invoke(any(), any(), any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withAddMemberToMLSGroupSucceeds(): Arrangement = apply {
            coEvery {
                mlsConversationRepository.addMemberToMLSGroup(any(), any(), any(), any())
            }.returns(Either.Right(Unit))
        }

        fun arrange() = useCase to this
    }
}
