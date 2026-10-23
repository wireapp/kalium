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
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.CryptoQualifiedID
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.Recipient
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
import dev.mokkery.answering.calls
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
import kotlin.test.assertContentEquals
import com.wire.kalium.messaging.sending.MLSMessageCreatorImpl
import com.wire.kalium.messaging.sending.MessageTarget
import kotlinx.coroutines.flow.first

class MLSMessageCreatorTest {

    @Test
    fun givenMessageAndMLSConversationExists_whenCreatingMLSMessage_thenMLSClientShouldBeUsedToEncryptProtobufContent() = runTest {
        val encryptedData = byteArrayOf()
        val plainData = byteArrayOf(0x42, 0x73)

        val (arrangement, creator) = Arrangement()
            .withObserveLegalHoldStatus(Either.Right(Conversation.LegalHoldStatus.DISABLED))
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
    }

    @Test
    fun givenMessageAndConversationDoesNotExist_whenCreatingMLSMessage_thenShouldAttemptToJoinExistingBeforeEncryptContent() = runTest {
        val encryptedData = byteArrayOf()
        val plainData = byteArrayOf(0x42, 0x73)

        val (arrangement, creator) = Arrangement()
            .withObserveLegalHoldStatus(Either.Right(Conversation.LegalHoldStatus.DISABLED))
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
    }

    @Test
    fun givenUsersTarget_whenCreatingTargetedMLSMessage_thenEncryptForEachGroupClientOfTargetedUsersExceptSender() = runTest {
        val plainData = byteArrayOf(0x42, 0x73)
        val (arrangement, creator) = Arrangement()
            .withObserveLegalHoldStatus(Either.Right(Conversation.LegalHoldStatus.DISABLED))
            .withEncodeToProtobufReturning(plainData)
            .withMLSGroupConversationExisting(true)
            .withCommitPendingProposals(Either.Right(Unit))
            .withGroupMembers(GROUP_MEMBERS)
            .withMLSEncryptTargetedMessage()
            .arrange {}

        creator.prepareMLSGroupAndCreateOutgoingTargetedMLSMessage(
            arrangement.transactionContext,
            GROUP_ID,
            TestMessage.TEXT_MESSAGE,
            MessageTarget.Users(TestMessage.TEST_SENDER_USER_ID, OTHER_USER_ID),
            transient = false
        ).shouldSucceed { batch ->
            assertContentEquals(
                ciphertextFor(SENDER_OTHER_CLIENT) + ciphertextFor(OTHER_USER_CLIENT_1) + ciphertextFor(OTHER_USER_CLIENT_2),
                batch.value
            )
        }

        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.encryptTargetedMessage(any(), any(), eq(SENDER_CLIENT), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.encryptTargetedMessage(any(), any(), eq(UNTARGETED_USER_CLIENT), any(), any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.encryptTargetedMessage(
                eq(arrangement.mlsContext),
                eq(GROUP_ID),
                eq(OTHER_USER_CLIENT_1),
                eq(plainData),
                eq(false)
            )
        }
    }

    @Test
    fun givenClientTarget_whenCreatingTargetedMLSMessage_thenEncryptOnlyForTargetedClientsInGroup() = runTest {
        val (arrangement, creator) = Arrangement()
            .withObserveLegalHoldStatus(Either.Right(Conversation.LegalHoldStatus.DISABLED))
            .withEncodeToProtobufReturning(byteArrayOf(0x42))
            .withMLSGroupConversationExisting(true)
            .withCommitPendingProposals(Either.Right(Unit))
            .withGroupMembers(GROUP_MEMBERS)
            .withMLSEncryptTargetedMessage()
            .arrange {}

        val target = MessageTarget.Client(
            listOf(
                Recipient(OTHER_USER_ID, listOf(ClientId(OTHER_USER_CLIENT_2.value), ClientId("not-in-group"))),
                Recipient(UserId("not-in-group", "domain"), listOf(ClientId(OTHER_USER_CLIENT_1.value)))
            )
        )

        creator.prepareMLSGroupAndCreateOutgoingTargetedMLSMessage(
            arrangement.transactionContext,
            GROUP_ID,
            TestMessage.TEXT_MESSAGE,
            target,
            transient = false
        ).shouldSucceed { batch ->
            assertContentEquals(ciphertextFor(OTHER_USER_CLIENT_2), batch.value)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.encryptTargetedMessage(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenConversationTargetIgnoringUsers_whenCreatingTargetedMLSMessage_thenEncryptForGroupClientsExceptIgnoredUsersAndSender() =
        runTest {
            val (arrangement, creator) = Arrangement()
                .withObserveLegalHoldStatus(Either.Right(Conversation.LegalHoldStatus.DISABLED))
                .withEncodeToProtobufReturning(byteArrayOf(0x42))
                .withMLSGroupConversationExisting(true)
                .withCommitPendingProposals(Either.Right(Unit))
                .withGroupMembers(GROUP_MEMBERS)
                .withMLSEncryptTargetedMessage()
                .arrange {}

            creator.prepareMLSGroupAndCreateOutgoingTargetedMLSMessage(
                arrangement.transactionContext,
                GROUP_ID,
                TestMessage.TEXT_MESSAGE,
                MessageTarget.Conversation(usersToIgnore = setOf(OTHER_USER_ID)),
                transient = false
            ).shouldSucceed { batch ->
                assertContentEquals(ciphertextFor(SENDER_OTHER_CLIENT) + ciphertextFor(UNTARGETED_USER_CLIENT), batch.value)
            }
        }

    @Test
    fun givenTargetWithoutGroupMembers_whenCreatingTargetedMLSMessage_thenReturnEmptyBatch() = runTest {
        val (arrangement, creator) = Arrangement()
            .withObserveLegalHoldStatus(Either.Right(Conversation.LegalHoldStatus.DISABLED))
            .withEncodeToProtobufReturning(byteArrayOf(0x42))
            .withMLSGroupConversationExisting(true)
            .withCommitPendingProposals(Either.Right(Unit))
            .withGroupMembers(GROUP_MEMBERS)
            .withMLSEncryptTargetedMessage()
            .arrange {}

        creator.prepareMLSGroupAndCreateOutgoingTargetedMLSMessage(
            arrangement.transactionContext,
            GROUP_ID,
            TestMessage.TEXT_MESSAGE,
            MessageTarget.Users(UserId("not-in-group", "domain")),
            transient = false
        ).shouldSucceed { batch ->
            assertContentEquals(ByteArray(0), batch.value)
        }

        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.encryptTargetedMessage(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenMessage_whenCreatingTransientMLSMessage_thenEncryptAsTransientMessage() = runTest {
        val plainData = byteArrayOf(0x42, 0x73)
        val encryptedData = byteArrayOf(0x01)
        val (arrangement, creator) = Arrangement()
            .withObserveLegalHoldStatus(Either.Right(Conversation.LegalHoldStatus.DISABLED))
            .withEncodeToProtobufReturning(plainData)
            .withMLSGroupConversationExisting(true)
            .withCommitPendingProposals(Either.Right(Unit))
            .withMLSEncryptTransientMessage(encryptedData)
            .arrange {}

        creator.prepareMLSGroupAndCreateOutgoingTransientMLSMessage(arrangement.transactionContext, GROUP_ID, TestMessage.TEXT_MESSAGE)
            .shouldSucceed { assertContentEquals(encryptedData, it.value) }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.encryptTransientMessage(eq(arrangement.mlsContext), eq(GROUP_ID), eq(plainData))
        }
        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.encryptMessage(any(), any(), any())
        }
    }

    @Test
    fun givenUsersTarget_whenCreatingTransientTargetedMLSMessage_thenEncryptTransientTargetedMessages() = runTest {
        val plainData = byteArrayOf(0x42)
        val (arrangement, creator) = Arrangement()
            .withObserveLegalHoldStatus(Either.Right(Conversation.LegalHoldStatus.DISABLED))
            .withEncodeToProtobufReturning(plainData)
            .withMLSGroupConversationExisting(true)
            .withCommitPendingProposals(Either.Right(Unit))
            .withGroupMembers(GROUP_MEMBERS)
            .withMLSEncryptTargetedMessage()
            .arrange {}

        creator.prepareMLSGroupAndCreateOutgoingTargetedMLSMessage(
            arrangement.transactionContext,
            GROUP_ID,
            TestMessage.TEXT_MESSAGE,
            MessageTarget.Users(OTHER_USER_ID),
            transient = true
        ).shouldSucceed { batch ->
            assertContentEquals(ciphertextFor(OTHER_USER_CLIENT_1) + ciphertextFor(OTHER_USER_CLIENT_2), batch.value)
        }

        verifySuspend(VerifyMode.exactly(2)) {
            arrangement.mlsConversationRepository.encryptTargetedMessage(any(), eq(GROUP_ID), any(), eq(plainData), eq(true))
        }
        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.encryptTargetedMessage(any(), any(), any(), any(), eq(false))
        }
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val protoContentMapper = mock<ProtoContentMapper>(mode = MockMode.autoUnit)
        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val mlsConversationRepository = mock<MLSConversationRepository>(mode = MockMode.autoUnit)
        val joinExistingConversationUseCase = mock<JoinExistingMLSConversationUseCase>(mode = MockMode.autoUnit)

        suspend fun arrange(block: suspend Arrangement.() -> Unit = {}) = apply {
            block()
        }.let {
            this to MLSMessageCreatorImpl(
                legalHoldStatus = { conversationRepository.observeLegalHoldStatus(it).first() },
                commitPendingProposals = mlsConversationRepository::commitPendingProposals,
                joinConversation = { context, conversationId -> joinExistingConversationUseCase(context, conversationId) },
                encrypt = mlsConversationRepository::encryptMessage,
                encryptTransient = mlsConversationRepository::encryptTransientMessage,
                encryptTargeted = mlsConversationRepository::encryptTargetedMessage,
                selfUserId = SELF_USER_ID,
                protoContentMapper = protoContentMapper
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

        suspend fun withMLSEncryptMessage(data: ByteArray) = apply {
            everySuspend { mlsConversationRepository.encryptMessage(any(), any(), any()) } returns Either.Right(data)
        }

        suspend fun withMLSEncryptTransientMessage(data: ByteArray) = apply {
            everySuspend { mlsConversationRepository.encryptTransientMessage(any(), any(), any()) } returns Either.Right(data)
        }

        suspend fun withMLSEncryptTargetedMessage() = apply {
            everySuspend { mlsConversationRepository.encryptTargetedMessage(any(), any(), any(), any(), any()) } calls { args ->
                Either.Right(ciphertextFor(args.args[2] as CryptoQualifiedClientId))
            }
        }

        suspend fun withGroupMembers(members: List<CryptoQualifiedClientId>) = apply {
            everySuspend { mlsContext.members(any()) } returns members
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
        val OTHER_USER_ID = UserId("other-user", "other-domain")
        val SENDER_CLIENT = cryptoClientId(TestMessage.TEST_SENDER_USER_ID, TestMessage.TEST_SENDER_CLIENT_ID.value)
        val SENDER_OTHER_CLIENT = cryptoClientId(TestMessage.TEST_SENDER_USER_ID, "sender-other-client")
        val OTHER_USER_CLIENT_1 = cryptoClientId(OTHER_USER_ID, "other-client-1")
        val OTHER_USER_CLIENT_2 = cryptoClientId(OTHER_USER_ID, "other-client-2")
        val UNTARGETED_USER_CLIENT = cryptoClientId(UserId("untargeted-user", "domain"), "untargeted-client")
        val GROUP_MEMBERS = listOf(SENDER_CLIENT, SENDER_OTHER_CLIENT, OTHER_USER_CLIENT_1, OTHER_USER_CLIENT_2, UNTARGETED_USER_CLIENT)

        fun cryptoClientId(userId: UserId, clientId: String) =
            CryptoQualifiedClientId(clientId, CryptoQualifiedID(userId.value, userId.domain))

        fun ciphertextFor(recipient: CryptoQualifiedClientId) = "<$recipient>".encodeToByteArray()
    }

}
