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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.LegalHoldStatusMapper
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MLSMessageCreatorTest {

    @Test
    fun givenMessageAndMLSConversationExists_whenCreatingMLSMessage_thenMLSClientShouldBeUsedToEncryptProtobufContent() = runTest {
        val encryptedData = byteArrayOf()
        val plainData = byteArrayOf(0x42, 0x73)

        val (arrangement, creator) = Arrangement()
            .withObserveLegalHoldStatus(Either.Right(Conversation.LegalHoldStatus.DISABLED))
            .withMapLegalHoldConversationStatus(Conversation.LegalHoldStatus.DISABLED)
            .withEncodeToProtobufReturning(plainData)
            .withMLSGroupConversationExisting(true)
            .withCommitPendingProposals(Either.Right(Unit))
            .withMLSEncryptMessage(encryptedData)
            .arrange {}

        creator.prepareMLSGroupAndCreateOutgoingMLSMessage(arrangement.transactionContext, GROUP_ID, TestMessage.TEXT_MESSAGE)
            .shouldSucceed {}

        coVerify {
            arrangement.mlsContext.encryptMessage(eq(CRYPTO_GROUP_ID), eq(plainData))
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsConversationRepository.commitPendingProposals(any(), eq(GROUP_ID))
        }.wasInvoked(once)

        coVerify {
            arrangement.conversationRepository.observeLegalHoldStatus(any())
        }.wasInvoked(once)

        verify {
            arrangement.legalHoldStatusMapper.mapLegalHoldConversationStatus(any(), any())
        }.wasInvoked(once)
    }

    @Test
    fun givenMessageAndConversationDoesNotExist_whenCreatingMLSMessage_thenShouldAttemptToJoinExistingBeforeEncryptContent() = runTest {
        val encryptedData = byteArrayOf()
        val plainData = byteArrayOf(0x42, 0x73)

        val (arrangement, creator) = Arrangement()
            .withObserveLegalHoldStatus(Either.Right(Conversation.LegalHoldStatus.DISABLED))
            .withMapLegalHoldConversationStatus(Conversation.LegalHoldStatus.DISABLED)
            .withEncodeToProtobufReturning(plainData)
            .withMLSGroupConversationExisting(false)
            .withJoinExistingConversation(Either.Right(Unit))
            .withMLSEncryptMessage(encryptedData)
            .arrange {}

        creator.prepareMLSGroupAndCreateOutgoingMLSMessage(arrangement.transactionContext, GROUP_ID, TestMessage.TEXT_MESSAGE)
            .shouldSucceed {}

        coVerify {
            arrangement.joinExistingConversationUseCase(any(), eq(TestMessage.TEXT_MESSAGE.conversationId), any())
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsContext.encryptMessage(eq(CRYPTO_GROUP_ID), eq(plainData))
        }.wasInvoked(once)

        coVerify {
            arrangement.conversationRepository.observeLegalHoldStatus(any())
        }.wasInvoked(once)

        verify {
            arrangement.legalHoldStatusMapper.mapLegalHoldConversationStatus(any(), any())
        }.wasInvoked(once)
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val protoContentMapper = mock(ProtoContentMapper::class)
        val conversationRepository = mock(ConversationRepository::class)
        val mlsConversationRepository = mock(MLSConversationRepository::class)
        val joinExistingConversationUseCase = mock(JoinExistingMLSConversationUseCase::class)
        val legalHoldStatusMapper = mock(LegalHoldStatusMapper::class)

        suspend fun arrange(block: suspend Arrangement.() -> Unit = {}) = apply {
            block()
        }.let {
            this to MLSMessageCreatorImpl(
                conversationRepository,
                legalHoldStatusMapper,
                mlsConversationRepository,
                joinExistingConversationUseCase,
                SELF_USER_ID,
                protoContentMapper
            )
        }

        suspend fun withObserveLegalHoldStatus(result: Either<StorageFailure, Conversation.LegalHoldStatus>) = apply {
            coEvery {
                conversationRepository.observeLegalHoldStatus(any())
            }.returns(flowOf(result))
        }

        fun withEncodeToProtobufReturning(plainData: ByteArray) = apply {
            every {
                protoContentMapper.encodeToProtobuf(any())
            }.returns(PlainMessageBlob(plainData))
        }

        fun withMapLegalHoldConversationStatus(result: Conversation.LegalHoldStatus) = apply {
            every {
                legalHoldStatusMapper.mapLegalHoldConversationStatus(any(), any())
            }.returns(result)
        }

        suspend fun withMLSEncryptMessage(data: ByteArray) = apply {
            coEvery { mlsContext.encryptMessage(any(), any()) }
                .returns(data)
        }

        suspend fun withCommitPendingProposals(result: Either<CoreFailure, Unit> = Either.Right(Unit)) = apply {
            coEvery {
                mlsConversationRepository.commitPendingProposals(any(), any())
            }.returns(result)
        }

        suspend fun withMLSGroupConversationExisting(doesConversationExist: Boolean = true) = apply {
            coEvery {
                mlsContext.conversationExists(any())
            }.returns(doesConversationExist)
        }

        suspend fun withJoinExistingConversation(result: Either<CoreFailure, Unit> = Either.Right(Unit)) = apply {
            coEvery {
                joinExistingConversationUseCase(any(), any(), any())
            }.returns(result)
        }

    }

    private companion object {
        val SELF_USER_ID = UserId("user-id", "domain")
        val GROUP_ID = GroupID("groupId")
        val CRYPTO_GROUP_ID = MapperProvider.idMapper().toCryptoModel(GroupID("groupId"))
    }

}
