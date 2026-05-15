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
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.encryptMessage(eq(arrangement.mlsContext), eq(GROUP_ID), eq(plainData))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.commitPendingProposals(any(), eq(GROUP_ID))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.observeLegalHoldStatus(any())
        }

        verify(VerifyMode.exactly(1)) {
            arrangement.legalHoldStatusMapper.mapLegalHoldConversationStatus(any(), any())
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.joinExistingConversationUseCase(any(), eq(TestMessage.TEXT_MESSAGE.conversationId), any(), eq(true))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.encryptMessage(eq(arrangement.mlsContext), eq(GROUP_ID), eq(plainData))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.observeLegalHoldStatus(any())
        }

        verify(VerifyMode.exactly(1)) {
            arrangement.legalHoldStatusMapper.mapLegalHoldConversationStatus(any(), any())
        }
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val protoContentMapper = mock<ProtoContentMapper>(mode = MockMode.autoUnit)
        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val mlsConversationRepository = mock<MLSConversationRepository>(mode = MockMode.autoUnit)
        val joinExistingConversationUseCase = mock<JoinExistingMLSConversationUseCase>(mode = MockMode.autoUnit)
        val legalHoldStatusMapper = mock<LegalHoldStatusMapper>(mode = MockMode.autoUnit)

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
            everySuspend {
                conversationRepository.observeLegalHoldStatus(any())
            } returns flowOf(result)
        }

        fun withEncodeToProtobufReturning(plainData: ByteArray) = apply {
            every {
                protoContentMapper.encodeToProtobuf(any())
            } returns PlainMessageBlob(plainData)
        }

        fun withMapLegalHoldConversationStatus(result: Conversation.LegalHoldStatus) = apply {
            every {
                legalHoldStatusMapper.mapLegalHoldConversationStatus(any(), any())
            } returns result
        }

        suspend fun withMLSEncryptMessage(data: ByteArray) = apply {
            everySuspend { mlsConversationRepository.encryptMessage(any(), any(), any()) } returns Either.Right(data)
        }

        suspend fun withCommitPendingProposals(result: Either<CoreFailure, Unit> = Either.Right(Unit)) = apply {
            everySuspend {
                mlsConversationRepository.commitPendingProposals(any(), any())
            } returns result
        }

        suspend fun withMLSGroupConversationExisting(doesConversationExist: Boolean = true) = apply {
            everySuspend {
                mlsContext.conversationExists(any())
            } returns doesConversationExist
        }

        suspend fun withJoinExistingConversation(result: Either<CoreFailure, Unit> = Either.Right(Unit)) = apply {
            everySuspend {
                joinExistingConversationUseCase(any(), any(), any(), eq(true))
            } returns result
        }

    }

    private companion object {
        val SELF_USER_ID = UserId("user-id", "domain")
        val GROUP_ID = GroupID("groupId")
        val CRYPTO_GROUP_ID = MapperProvider.idMapper().toCryptoModel(GroupID("groupId"))
    }

}
