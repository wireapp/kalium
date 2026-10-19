/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CommitBundle
import com.wire.kalium.cryptography.CoreCryptoCentral
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.CryptoQualifiedID
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.cryptography.MLSCiphersuite
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.MLSEpochObserver
import com.wire.kalium.cryptography.MLSTransporter
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.ProteusCoreCryptoContext
import com.wire.kalium.cryptography.coreCryptoCentral
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.DecryptedMessageBundle
import com.wire.kalium.logic.data.conversation.LeaveSubconversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.SubconversationRepositoryImpl
import com.wire.kalium.logic.data.conversation.commitPendingMLSProposals
import com.wire.kalium.logic.data.conversation.decryptMLSMessage
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.PendingSubconversationProposalsImpl
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.feature.message.PendingProposalSchedulerImpl
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.incremental.EventSource
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.network.api.authenticated.conversation.SubconversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.TestUserDatabase
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class MLSSubconversationNativeRecoveryTest {

    @Test
    fun missingMappingIsRecoveredBeforeNativeSubgroupCommitIsDecrypted() = runTest(TestKaliumDispatcher.default) {
        val root = Files.createTempDirectory("kalium-subconversation-recovery")
        val database = TestUserDatabase(TestUser.SELF.id.toDao(), TestKaliumDispatcher.default)
        var sender: NativeClient? = null
        var receiver: NativeClient? = null

        try {
            val senderClient = createNativeClient(root.resolve("sender"), SENDER_CLIENT, this).also { sender = it }
            val receiverClient = createNativeClient(root.resolve("receiver"), RECEIVER_CLIENT, this).also { receiver = it }
            val commit = establishSubgroupAndCreateCommit(senderClient, receiverClient)
            val arrangement = Arrangement(database, commit)

            assertNull(
                arrangement.subconversations.getSubconversationInfo(PARENT_ID, SUBCONVERSATION_ID),
                "The fixture must begin without a durable subgroup mapping",
            )

            val parentFailure = receiverClient.client.transaction("verify-parent-fallback") { mlsContext ->
                decryptMLSMessage(mlsContext, commit, PARENT_GROUP_ID)
            }
            assertEquals(
                MLSFailure.InvalidGroupId,
                assertIs<Either.Left<CoreFailure>>(parentFailure).value,
                "The synthetic parent group must reject ciphertext from the subgroup",
            )

            val handlerResult = receiverClient.client.transaction("receive-subgroup-commit") { mlsContext ->
                arrangement.handler.handleNewMLSMessage(
                    NativeTransactionContext(mlsContext),
                    arrangement.event,
                    EventDeliveryInfo(EventSource.LIVE),
                )
            }

            assertEquals(Either.Right(Unit), handlerResult)
            assertEquals(
                SUBGROUP_ID,
                arrangement.subconversations.getSubconversationInfo(PARENT_ID, SUBCONVERSATION_ID),
                "The handler must durably recover the subgroup mapping before decrypting",
            )
            assertEquals(listOf(SUBGROUP_ID), arrangement.decryptionGroups)
            val decrypted = assertIs<Either.Right<List<DecryptedMessageBundle>>>(arrangement.decryptionResults.single())
            assertIs<DecryptedMessageBundle.Commit>(decrypted.value.single())
        } finally {
            receiver?.close()
            sender?.close()
            database.builder.close()
            database.delete()
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun committedLeaveWithoutSqlAcknowledgementIsRetiredByNextOwner() = runTest(TestKaliumDispatcher.default) {
        val root = Files.createTempDirectory("kalium-leave-retirement")
        val database = TestUserDatabase(TestUser.SELF.id.toDao(), TestKaliumDispatcher.default)
        val native = createNativeClient(root.resolve("native"), RECEIVER_CLIENT, this)
        try {
            val queue = PendingSubconversationProposalsImpl(database.builder.pendingSubconversationProposalDAO)
            val api = mock<ConversationApi>()
            val subgroups = SubconversationRepositoryImpl(api, database.builder.subconversationDAO)
            val clientId = mock<CurrentClientIdProvider>()
            everySuspend { clientId.invoke() } returns Either.Right(ClientId("123456abcdef"))
            everySuspend { api.leaveSubconversation(any(), any()) } returns NetworkResponse.Success(Unit, emptyMap(), 200)
            val leave = LeaveSubconversationUseCaseImpl(api, subgroups, TestUser.SELF.id, clientId, queue)
            native.client.transaction("create-before-leave") {
                it.createConversation(SUBGROUP_ID.value, EXTERNAL_SENDER_KEY)
            }
            subgroups.insertSubconversation(PARENT_ID, SUBCONVERSATION_ID, SUBGROUP_ID)
            queue.schedule(SUBGROUP_ID, Instant.DISTANT_PAST)
            val originalToken = queue.current(SUBGROUP_ID)!!.token
            native.client.transaction("committed-leave") { context ->
                assertIs<Either.Right<*>>(leave(context, PARENT_ID, SUBCONVERSATION_ID))
            }
            assertFalse(native.client.transaction("check-left") { it.conversationExists(SUBGROUP_ID.value) })
            assertNull(subgroups.getSubconversationInfo(PARENT_ID, SUBCONVERSATION_ID))
            // Interrupt the protocol after native commit by omitting the caller's SQL acknowledgement.
            val restartedQueue = PendingSubconversationProposalsImpl(database.builder.pendingSubconversationProposalDAO)
            assertEquals(originalToken, restartedQueue.current(SUBGROUP_ID)?.token)
            val provider = mock<CryptoTransactionProvider>()
            everySuspend { provider.transaction<Unit>(any(), any()) } calls {
                @Suppress("UNCHECKED_CAST")
                val callback = it.args[1] as suspend (CryptoTransactionContext) -> Either<CoreFailure, Unit>
                native.client.transaction("resume-proposal") { context -> callback(NativeTransactionContext(context)) }
            }
            val mls = mock<MLSConversationRepository>()
            everySuspend { mls.observeProposalTimers() } returns emptyFlow()
            var commitsToMissingGroup = 0
            everySuspend { mls.commitPendingProposals(any(), any()) } calls {
                commitsToMissingGroup++
                commitPendingMLSProposals(it.args[0] as MlsCoreCryptoContext, it.args[1] as GroupID, database.builder.conversationDAO)
            }
            val sync = InMemoryIncrementalSyncRepository()
            PendingProposalSchedulerImpl(
                sync, lazy { mls }, lazy { subgroups }, provider, restartedQueue,
                TestKaliumDispatcher, backgroundScope.coroutineContext,
            )
            sync.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            // CoreCrypto runs on native threads; virtual time must not outrun its transaction.
            val retired = withContext(Dispatchers.Default) {
                withTimeoutOrNull(12.seconds) {
                    while (restartedQueue.current(SUBGROUP_ID) != null) delay(100)
                    true
                }
            }
            assertNotNull(retired, "A committed leave must not leave a proposal retrying a permanently absent native group")
            assertEquals(0, commitsToMissingGroup)
            sync.updateIncrementalSyncState(IncrementalSyncStatus.Pending)
        } finally {
            native.close()
            database.builder.close()
            database.delete()
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun failedOuterLeaveTransactionPreservesNativeGroupAndDurableIntent() = runTest(TestKaliumDispatcher.default) {
        val root = Files.createTempDirectory("kalium-leave-rollback")
        val database = TestUserDatabase(TestUser.SELF.id.toDao(), TestKaliumDispatcher.default)
        val native = createNativeClient(root.resolve("native"), RECEIVER_CLIENT, this)
        try {
            val queue = PendingSubconversationProposalsImpl(database.builder.pendingSubconversationProposalDAO)
            val api = mock<ConversationApi>()
            val subgroups = SubconversationRepositoryImpl(api, database.builder.subconversationDAO)
            val clientId = mock<CurrentClientIdProvider>()
            everySuspend { clientId.invoke() } returns Either.Right(ClientId("123456abcdef"))
            everySuspend { api.leaveSubconversation(any(), any()) } returns NetworkResponse.Success(Unit, emptyMap(), 200)
            val leave = LeaveSubconversationUseCaseImpl(api, subgroups, TestUser.SELF.id, clientId, queue)
            native.client.transaction("create-before-rollback") {
                it.createConversation(SUBGROUP_ID.value, EXTERNAL_SENDER_KEY)
            }
            subgroups.insertSubconversation(PARENT_ID, SUBCONVERSATION_ID, SUBGROUP_ID)
            queue.schedule(SUBGROUP_ID, Instant.DISTANT_PAST)
            val originalToken = queue.current(SUBGROUP_ID)!!.token

            assertFailsWith<IllegalStateException> {
                native.client.transaction("rolled-back-leave") { context ->
                    assertIs<Either.Right<*>>(leave(context, PARENT_ID, SUBCONVERSATION_ID))
                    error("Abort after wipe, before native commit")
                }
            }

            assertTrue(native.client.transaction("check-rollback") { it.conversationExists(SUBGROUP_ID.value) })
            val persisted = PendingSubconversationProposalsImpl(database.builder.pendingSubconversationProposalDAO).current(SUBGROUP_ID)
            assertNotNull(persisted)
            assertEquals(originalToken, persisted.token)
            assertTrue(persisted.leaveRequested)
        } finally {
            native.close()
            database.builder.close()
            database.delete()
            root.toFile().deleteRecursively()
        }
    }

    private suspend fun establishSubgroupAndCreateCommit(sender: NativeClient, receiver: NativeClient): ByteArray {
        val receiverKeyPackage = receiver.client.transaction("receiver-key-package") {
            it.generateKeyPackages(1).single()
        }
        sender.client.transaction("create-subgroup") {
            it.createConversation(SUBGROUP_ID.value, EXTERNAL_SENDER_KEY)
            it.addMember(SUBGROUP_ID.value, listOf(receiverKeyPackage))
        }
        val welcome = checkNotNull(sender.commits.receive().welcome)
        val joinedGroup = receiver.client.transaction("join-subgroup") {
            it.processWelcomeMessage(welcome)
        }
        assertEquals(SUBGROUP_ID.value, joinedGroup)
        receiver.client.transaction("create-parent-group") {
            it.createConversation(PARENT_GROUP_ID.value, EXTERNAL_SENDER_KEY)
        }

        sender.client.transaction("create-subgroup-commit") {
            it.updateKeyingMaterial(SUBGROUP_ID.value)
        }
        return sender.commits.receive().commit
    }

    private suspend fun createNativeClient(
        store: Path,
        clientId: CryptoQualifiedClientId,
        scope: CoroutineScope,
    ): NativeClient {
        val central = coreCryptoCentral(store.toString(), ByteArray(32))
        val commits = Channel<CommitBundle>(Channel.UNLIMITED)
        return try {
            val client = central.mlsClient(
                clientId,
                MLSCiphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519,
                object : MLSTransporter {
                    override suspend fun sendCommitBundle(commitBundle: CommitBundle) {
                        commits.send(commitBundle)
                    }
                },
                object : MLSEpochObserver {
                    override suspend fun onEpochChange(groupId: String, epoch: ULong) = Unit
                },
                scope,
            )
            client.initializeBasicCredential()
            NativeClient(central, client, commits)
        } catch (failure: Throwable) {
            commits.close()
            central.close()
            throw failure
        }
    }

    private class NativeClient(
        private val central: CoreCryptoCentral,
        val client: MLSClient,
        val commits: Channel<CommitBundle>,
    ) {
        suspend fun close() {
            commits.close()
            client.close()
            central.close()
        }
    }

    private class NativeTransactionContext(
        override val mls: MlsCoreCryptoContext,
    ) : CryptoTransactionContext {
        override val proteus: ProteusCoreCryptoContext = mock(mode = MockMode.autoUnit)
    }

    private class Arrangement(database: TestUserDatabase, commit: ByteArray) {
        private val api = mock<ConversationApi>()
        private val conversations = mock<ConversationRepository>()
        private val mlsConversations = mock<MLSConversationRepository>()
        private val pendingProposals = mock<PendingProposalScheduler>(mode = MockMode.autoUnit)
        private val staleEpochVerifier = mock<StaleEpochVerifier>()
        val subconversations = SubconversationRepositoryImpl(api, database.builder.subconversationDAO)
        val decryptionGroups = mutableListOf<GroupID>()
        val decryptionResults = mutableListOf<Either<CoreFailure, List<DecryptedMessageBundle>>>()
        val event = Event.Conversation.NewMLSMessage(
            id = "native-subgroup-commit",
            conversationId = PARENT_ID,
            subconversationId = SUBCONVERSATION_ID,
            senderUserId = TestUser.USER_ID,
            messageInstant = EVENT_TIME,
            content = Base64.encode(commit),
        )
        private val unpacker = MLSMessageUnpackerImpl(
            conversations,
            subconversations,
            mlsConversations,
            pendingProposals,
            TestUser.SELF.id,
            mock<ProtoContentMapper>(),
        )
        val handler = NewMessageEventHandlerImpl(
            mock(),
            unpacker,
            mock<ApplicationMessageHandler>(mode = MockMode.autoUnit),
            mock<LegalHoldHandler>(mode = MockMode.autoUnit),
            { _, _ -> },
            { _, _ -> },
            TestUser.SELF.id,
            staleEpochVerifier,
            mock<ResetMLSConversationUseCase>(mode = MockMode.autoUnit),
        )

        init {
            everySuspend { conversations.getConversationProtocolInfo(PARENT_ID) } returns
                Either.Right(TestConversation.MLS_PROTOCOL_INFO.copy(groupId = PARENT_GROUP_ID))
            everySuspend { api.fetchSubconversationDetails(PARENT_ID.toApi(), SUBCONVERSATION_ID.toApi()) } returns
                NetworkResponse.Success(
                    SubconversationResponse(
                        id = SUBCONVERSATION_ID.toApi(),
                        parentId = PARENT_ID.toApi(),
                        groupId = SUBGROUP_ID.value,
                        epoch = 1UL,
                        epochTimestamp = null,
                        mlsCipherSuiteTag = null,
                        members = emptyList(),
                    ),
                    emptyMap(),
                    200,
                )
            everySuspend { mlsConversations.decryptMessage(any(), any(), any()) } calls {
                val mlsContext = it.args[0] as MlsCoreCryptoContext
                val message = it.args[1] as ByteArray
                val groupId = it.args[2] as GroupID
                decryptionGroups += groupId
                decryptMLSMessage(mlsContext, message, groupId).also(decryptionResults::add)
            }
            everySuspend { staleEpochVerifier.verifyEpoch(any(), any(), any(), any()) } returns
                Either.Left(MLSFailure.InvalidGroupId)
        }
    }

    private companion object {
        val PARENT_ID = TestConversation.ID
        val SUBCONVERSATION_ID = SubconversationId("conference")
        val PARENT_GROUP_ID = GroupID(Base64.encode(ByteArray(32) { 1 }))
        val SUBGROUP_ID = GroupID(Base64.encode(ByteArray(32) { 2 }))
        val EVENT_TIME = Instant.parse("2026-10-05T10:00:00Z")
        val EXTERNAL_SENDER_KEY = ByteArray(32)
        val SENDER_CLIENT = CryptoQualifiedClientId(
            "abcdef012345",
            CryptoQualifiedID("00000000-0000-4000-8000-000000000001", "example.com"),
        )
        val RECEIVER_CLIENT = CryptoQualifiedClientId(
            "123456abcdef",
            CryptoQualifiedID("00000000-0000-4000-8000-000000000002", "example.com"),
        )
    }
}
