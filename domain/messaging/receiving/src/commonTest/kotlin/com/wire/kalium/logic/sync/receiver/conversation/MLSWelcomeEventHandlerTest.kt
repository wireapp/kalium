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

package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.common.error.CommonizedMLSException
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.cryptography.MLSGroupId
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.MLSWelcomeEventRepository
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.data.user.type.UserTypeInfo
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import dev.mokkery.answering.calls
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class MLSWelcomeEventHandlerTest {

    @Test
    fun givenMLSClientFailsProcessingOfWelcomeMessageFails_thenShouldNotMarkConversationAsEstablished() = runTest {
        val (arrangement, handler) = arrange {
            processWelcomeThrowable = RuntimeException("process failed")
        }

        assertIs<Either.Left<CoreFailure>>(handler.handle(arrangement.transactionContext, WELCOME_EVENT))

        assertEquals(emptyList(), arrangement.repository.updateCalls)
    }

    @Test
    fun givenConversationFetchFails_thenShouldNotMarkConversationAsEstablished() = runTest {
        val failure = CoreFailure.Unknown(null)
        val (arrangement, handler) = arrange {
            fetchResult = Either.Left(failure)
        }

        assertEquals(Either.Left(failure), handler.handle(arrangement.transactionContext, WELCOME_EVENT))

        assertEquals(emptyList(), arrangement.repository.updateCalls)
    }

    @Test
    fun givenConversationWasDeleted_whenHandlingWelcome_thenShouldSkipStaleEvent() = runTest {
        val (arrangement, handler) = arrange {
            fetchResult = Either.Left(noConversationFailure())
        }

        assertEquals(Either.Right(Unit), handler.handle(arrangement.transactionContext, WELCOME_EVENT))

        assertEquals(listOf("mls", "fetch"), arrangement.callOrder)
        assertEquals(emptyList(), arrangement.refillCalls)
    }

    @Test
    fun givenProcessingOfWelcomeSucceeds_thenShouldFetchConversationIfUnknown() = runTest {
        val (arrangement, handler) = arrange()

        assertEquals(Either.Right(Unit), handler.handle(arrangement.transactionContext, WELCOME_EVENT))

        val call = arrangement.fetchCalls.single()
        assertSame(arrangement.transactionContext, call.transactionContext)
        assertEquals(CONVERSATION_ID, call.conversationId)
    }

    @Test
    fun givenProcessingOfWelcomeSucceeds_thenShouldMarkConversationAsEstablished() = runTest {
        val (arrangement, handler) = arrange()

        assertEquals(Either.Right(Unit), handler.handle(arrangement.transactionContext, WELCOME_EVENT))

        assertEquals(
            listOf(UpdateCall(GroupID(MLS_GROUP_ID), Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED)),
            arrangement.repository.updateCalls
        )
    }

    @Test
    fun givenProcessingOfWelcomeForOneOnOneSucceeds_thenShouldResolveConversation() = runTest {
        val (arrangement, handler) = arrange {
            repository.observeResults = listOf(Either.Right(CONVERSATION_ONE_ONE))
        }

        assertEquals(Either.Right(Unit), handler.handle(arrangement.transactionContext, WELCOME_EVENT))

        val call = arrangement.resolveCalls.single()
        assertSame(arrangement.transactionContext, call.transactionContext)
        assertEquals(OTHER_USER, call.otherUser)
        assertTrue(call.invalidateCurrentKnownProtocols)
    }

    @Test
    fun givenProcessingOfWelcomeForGroupSucceeds_thenShouldNotResolveConversation() = runTest {
        val (arrangement, handler) = arrange()

        assertEquals(Either.Right(Unit), handler.handle(arrangement.transactionContext, WELCOME_EVENT))

        assertEquals(emptyList(), arrangement.resolveCalls)
    }

    @Test
    fun givenUpdateGroupStateFails_thenShouldPropagateError() = runTest {
        val failure = StorageFailure.DataNotFound
        val (arrangement, handler) = arrange {
            repository.updateResult = Either.Left(failure)
        }

        assertEquals(Either.Left(failure), handler.handle(arrangement.transactionContext, WELCOME_EVENT))
    }

    @Test
    fun givenResolveOneOnOneConversationFails_thenShouldPropagateError() = runTest {
        val failure = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, handler) = arrange {
            repository.observeResults = listOf(Either.Right(CONVERSATION_ONE_ONE))
            resolveResult = Either.Left(failure)
        }

        assertEquals(Either.Left(failure), handler.handle(arrangement.transactionContext, WELCOME_EVENT))
    }

    @Test
    fun givenResolveOneOnOneConversationFails_thenShouldNotAttemptToRefillKeyPackages() = runTest {
        val (arrangement, handler) = arrange {
            repository.observeResults = listOf(Either.Right(CONVERSATION_ONE_ONE))
            resolveResult = Either.Left(NetworkFailure.NoNetworkConnection(null))
        }

        handler.handle(arrangement.transactionContext, WELCOME_EVENT)

        assertEquals(emptyList(), arrangement.refillCalls)
    }

    @Test
    fun givenAllSucceeds_whenHandlingEvent_thenShouldAttemptToRefillKeyPackages() = runTest {
        val (arrangement, handler) = arrange()

        handler.handle(arrangement.transactionContext, WELCOME_EVENT)

        assertEquals(listOf(arrangement.mlsContext), arrangement.refillCalls)
    }

    @Test
    fun givenOrphanWelcomeAndLocalGroupAlreadyEstablished_whenHandlingWelcome_thenShouldSkipExternalCommitRejoin() = runTest {
        val (arrangement, handler) = arrange {
            withOrphanWelcome()
            repository.protocolResult = Either.Right(ESTABLISHED_PROTOCOL_INFO)
            conversationExists = true
        }

        assertEquals(Either.Right(Unit), handler.handle(arrangement.transactionContext, WELCOME_EVENT))

        assertEquals(emptyList(), arrangement.joinCalls)
        assertEquals(emptyList(), arrangement.repository.updateCalls)
        assertEquals(listOf(arrangement.mlsContext), arrangement.refillCalls)
    }

    @Test
    fun givenOrphanWelcomeAndLocalGroupNotEstablished_whenHandlingWelcome_thenShouldRejoinByExternalCommit() = runTest {
        val (arrangement, handler) = arrange {
            withOrphanWelcome()
            repository.protocolResult = Either.Right(PENDING_PROTOCOL_INFO)
        }

        assertEquals(Either.Right(Unit), handler.handle(arrangement.transactionContext, WELCOME_EVENT))

        val call = arrangement.joinCalls.single()
        assertSame(arrangement.transactionContext, call.transactionContext)
        assertEquals(CONVERSATION_ID, call.conversationId)
        assertEquals(emptyList(), arrangement.conversationExistsCalls)
    }

    @Test
    fun givenMLSContextIsNull_whenHandlingWelcome_thenNoOpBeforeAnyDependencyCall() = runTest {
        val (arrangement, handler) = arrange {
            mlsEnabled = false
        }

        assertEquals(Either.Right(Unit), handler.handle(arrangement.transactionContext, WELCOME_EVENT))

        assertEquals(listOf("mls"), arrangement.callOrder)
    }

    @Test
    fun givenDirectOneOnOneSuccess_whenHandling_thenExactOrderArgumentsAndFlowFirstArePreserved() = runTest {
        val (arrangement, handler) = arrange {
            repository.observeResults = listOf(
                Either.Right(CONVERSATION_ONE_ONE),
                Either.Right(CONVERSATION_GROUP),
            )
        }

        assertEquals(Either.Right(Unit), handler.handle(arrangement.transactionContext, WELCOME_EVENT))

        assertEquals(
            listOf("mls", "fetch", "process", "update", "observe", "resolve", "mls", "refill"),
            arrangement.callOrder
        )
        assertContentEquals(WELCOME, arrangement.processWelcomeCalls.single())
        assertSame(arrangement.transactionContext, arrangement.fetchCalls.single().transactionContext)
        assertEquals(CONVERSATION_ID, arrangement.fetchCalls.single().conversationId)
        assertEquals(1, arrangement.repository.observeEmissionCount)
        assertEquals(listOf(CONVERSATION_ID), arrangement.repository.observeCalls)
        assertTrue(arrangement.resolveCalls.single().invalidateCurrentKnownProtocols)
        assertSame(arrangement.mlsContext, arrangement.refillCalls.single())
    }

    @Test
    fun givenConversationAlreadyExists_whenHandlingWelcome_thenShouldDiscardAndRefill() = runTest {
        val (arrangement, handler) = arrange {
            processWelcomeThrowable = CommonizedMLSException(
                MLSFailure.ConversationAlreadyExists,
                IllegalStateException("already exists")
            )
        }

        assertEquals(Either.Right(Unit), handler.handle(arrangement.transactionContext, WELCOME_EVENT))

        assertEquals(listOf("mls", "fetch", "process", "mls", "refill"), arrangement.callOrder)
        assertEquals(emptyList(), arrangement.repository.protocolCalls)
        assertEquals(emptyList(), arrangement.joinCalls)
    }

    @Test
    fun givenOrphanWelcomeAndNonMLSProtocol_whenHandling_thenSkipsConversationExistsAndRejoins() = runTest {
        val (arrangement, handler) = arrange {
            withOrphanWelcome()
            repository.protocolResult = Either.Right(Conversation.ProtocolInfo.Proteus)
        }

        assertEquals(Either.Right(Unit), handler.handle(arrangement.transactionContext, WELCOME_EVENT))

        assertEquals(emptyList(), arrangement.conversationExistsCalls)
        assertEquals(1, arrangement.joinCalls.size)
    }

    @Test
    fun givenOrphanWelcomeAndProtocolInfoFailure_whenHandling_thenTreatsAsNotEstablishedAndRejoins() = runTest {
        val (arrangement, handler) = arrange {
            withOrphanWelcome()
            repository.protocolResult = Either.Left(StorageFailure.DataNotFound)
        }

        assertEquals(Either.Right(Unit), handler.handle(arrangement.transactionContext, WELCOME_EVENT))

        assertEquals(emptyList(), arrangement.conversationExistsCalls)
        assertEquals(1, arrangement.joinCalls.size)
    }

    @Test
    fun givenOrphanWelcomeAndConversationExistsFailure_whenHandling_thenTreatsAsNotEstablishedAndRejoins() = runTest {
        val (arrangement, handler) = arrange {
            withOrphanWelcome()
            repository.protocolResult = Either.Right(ESTABLISHED_PROTOCOL_INFO)
            conversationExistsThrowable = CommonizedMLSException(
                MLSFailure.ConversationNotFound,
                IllegalStateException("missing crypto group")
            )
        }

        assertEquals(Either.Right(Unit), handler.handle(arrangement.transactionContext, WELCOME_EVENT))

        assertEquals(listOf(GroupID(LOCAL_GROUP_ID).value), arrangement.conversationExistsCalls)
        assertEquals(1, arrangement.joinCalls.size)
    }

    @Test
    fun givenOrphanWelcomeAndJoinReturnsFailure_whenHandling_thenFailureRemainsLeftAndRefillIsSkipped() = runTest {
        val failure = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, handler) = arrange {
            withOrphanWelcome()
            repository.protocolResult = Either.Right(PENDING_PROTOCOL_INFO)
            joinResult = Either.Left(failure)
        }

        assertEquals(Either.Left(failure), handler.handle(arrangement.transactionContext, WELCOME_EVENT))

        assertEquals(1, arrangement.joinCalls.size)
        assertEquals(emptyList(), arrangement.refillCalls)
    }

    @Test
    fun givenReturnedFailuresAtShortCircuitStages_whenHandling_thenLaterWorkIsSkipped() = runTest {
        ReturnedFailureStage.entries.forEach { stage ->
            val failure = stage.failure
            val (arrangement, handler) = arrange {
                stage.configure(this, failure)
            }

            assertEquals(Either.Left(failure), handler.handle(arrangement.transactionContext, WELCOME_EVENT), stage.name)
            assertEquals(stage.expectedOrder, arrangement.callOrder, stage.name)
        }
    }

    @Test
    fun givenRefillReturnsFailure_whenHandlingSuccessfulWelcome_thenFailureIsIgnored() = runTest {
        val failure = CoreFailure.Unknown(IllegalStateException("refill failed"))
        val (arrangement, handler) = arrange {
            refillResult = Either.Left(failure)
        }

        assertEquals(Either.Right(Unit), handler.handle(arrangement.transactionContext, WELCOME_EVENT))

        assertEquals(listOf(arrangement.mlsContext), arrangement.refillCalls)
        assertEquals(listOf("mls", "fetch", "process", "update", "observe", "mls", "refill"), arrangement.callOrder)
    }

    @Test
    fun givenRefillFailsWithUnknownFailure_whenHandlingEvent_thenWelcomeStillSucceeds() = runTest {
        val (arrangement, handler) = arrange {
            refillResult = Either.Left(CoreFailure.Unknown(IllegalStateException("refill")))
        }

        assertEquals(Either.Right(Unit), handler.handle(arrangement.transactionContext, WELCOME_EVENT))

        assertEquals(listOf(arrangement.mlsContext), arrangement.refillCalls)
    }

    @Test
    fun givenRefillFailsWithNonUnknownFailure_whenHandlingEvent_thenWelcomeStillSucceeds() = runTest {
        val (arrangement, handler) = arrange {
            refillResult = Either.Left(StorageFailure.DataNotFound)
        }

        assertEquals(Either.Right(Unit), handler.handle(arrangement.transactionContext, WELCOME_EVENT))

        assertEquals(listOf(arrangement.mlsContext), arrangement.refillCalls)
    }

    @Test
    fun givenOrdinaryExceptionAtUnwrappedStage_whenHandling_thenSameExceptionEscapesAndLaterWorkIsSkipped() = runTest {
        EscapingFailureStage.entries.forEach { stage ->
            assertEscapingFailure(stage, IllegalStateException("$stage failed"))
        }
    }

    @Test
    fun givenCancellationAtAnyOperation_whenHandling_thenSameCancellationEscapesAndLaterWorkIsSkipped() = runTest {
        EscapingFailureStage.entries.forEach { stage ->
            assertEscapingFailure(stage, CancellationException("$stage cancelled"))
        }
        assertWrappedCancellation(ProcessOrExistsStage.PROCESS)
        assertWrappedCancellation(ProcessOrExistsStage.CONVERSATION_EXISTS)
    }

    @Test
    fun givenOrdinaryExceptionInsideMLSWrapper_whenHandling_thenExistingMappingBehaviorIsPreserved() = runTest {
        val processException = IllegalStateException("process failed")
        val (processArrangement, processHandler) = arrange {
            processWelcomeThrowable = processException
        }

        assertIs<Either.Left<CoreFailure>>(processHandler.handle(processArrangement.transactionContext, WELCOME_EVENT))
        assertEquals(listOf("mls", "fetch", "process"), processArrangement.callOrder)

        val existsException = IllegalStateException("exists failed")
        val (existsArrangement, existsHandler) = arrange {
            withOrphanWelcome()
            repository.protocolResult = Either.Right(ESTABLISHED_PROTOCOL_INFO)
            conversationExistsThrowable = existsException
        }

        assertEquals(Either.Right(Unit), existsHandler.handle(existsArrangement.transactionContext, WELCOME_EVENT))
        assertEquals(
            listOf("mls", "fetch", "process", "protocol", "conversation-exists", "join", "mls", "refill"),
            existsArrangement.callOrder
        )
    }

    private suspend fun assertEscapingFailure(stage: EscapingFailureStage, expected: Throwable) {
        val (arrangement, handler) = arrange {
            stage.configure(this, expected)
        }

        val actual = try {
            handler.handle(arrangement.transactionContext, WELCOME_EVENT)
            fail("Expected $expected to escape from $stage")
        } catch (actual: Throwable) {
            actual
        }

        assertSame(expected, actual, stage.name)
        assertEquals(stage.expectedOrder, arrangement.callOrder, stage.name)
    }

    private suspend fun assertWrappedCancellation(stage: ProcessOrExistsStage) {
        val expected = CancellationException("$stage cancelled")
        val (arrangement, handler) = arrange {
            when (stage) {
                ProcessOrExistsStage.PROCESS -> processWelcomeThrowable = expected
                ProcessOrExistsStage.CONVERSATION_EXISTS -> {
                    withOrphanWelcome()
                    repository.protocolResult = Either.Right(ESTABLISHED_PROTOCOL_INFO)
                    conversationExistsThrowable = expected
                }
            }
        }

        val actual = try {
            handler.handle(arrangement.transactionContext, WELCOME_EVENT)
            fail("Expected $expected to escape from $stage")
        } catch (actual: Throwable) {
            actual
        }

        assertSame(expected, actual)
        assertEquals(stage.expectedOrder, arrangement.callOrder)
    }

    private class Arrangement {
        val callOrder = mutableListOf<String>()
        val transactionContext = mock<CryptoTransactionContext>()
        val mlsContext = mock<MlsCoreCryptoContext>()
        val repository = MLSWelcomeRepositoryRecorder(callOrder)

        var mlsEnabled = true
        var processedGroupId = MLS_GROUP_ID
        var processWelcomeThrowable: Throwable? = null
        var conversationExists = false
        var conversationExistsThrowable: Throwable? = null
        var fetchResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        var fetchThrowable: Throwable? = null
        var resolveResult: Either<CoreFailure, ConversationId> = Either.Right(CONVERSATION_ID)
        var resolveThrowable: Throwable? = null
        var refillResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        var refillThrowable: Throwable? = null
        var joinResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        var joinThrowable: Throwable? = null

        val processWelcomeCalls = mutableListOf<ByteArray>()
        val conversationExistsCalls = mutableListOf<MLSGroupId>()
        val fetchCalls = mutableListOf<ConversationCall>()
        val resolveCalls = mutableListOf<ResolveCall>()
        val refillCalls = mutableListOf<MlsCoreCryptoContext>()
        val joinCalls = mutableListOf<ConversationCall>()

        init {
            every { transactionContext.mls } calls {
                callOrder += "mls"
                mlsContext.takeIf { mlsEnabled }
            }
            everySuspend { mlsContext.processWelcomeMessage(any()) } calls {
                callOrder += "process"
                processWelcomeCalls += (it.args[0] as ByteArray).copyOf()
                processWelcomeThrowable?.let { throwable -> throw throwable }
                processedGroupId
            }
            everySuspend { mlsContext.conversationExists(any()) } calls {
                callOrder += "conversation-exists"
                conversationExistsCalls += it.args[0] as MLSGroupId
                conversationExistsThrowable?.let { throwable -> throw throwable }
                conversationExists
            }
        }

        private suspend fun fetch(
            transactionContext: CryptoTransactionContext,
            conversationId: ConversationId,
        ): Either<CoreFailure, Unit> {
            callOrder += "fetch"
            fetchCalls += ConversationCall(transactionContext, conversationId)
            fetchThrowable?.let { throw it }
            return fetchResult
        }

        private suspend fun resolve(
            transactionContext: CryptoTransactionContext,
            otherUser: OtherUser,
            invalidateCurrentKnownProtocols: Boolean,
        ): Either<CoreFailure, ConversationId> {
            callOrder += "resolve"
            resolveCalls += ResolveCall(transactionContext, otherUser, invalidateCurrentKnownProtocols)
            resolveThrowable?.let { throw it }
            return resolveResult
        }

        private suspend fun refill(mlsContext: MlsCoreCryptoContext): Either<CoreFailure, Unit> {
            callOrder += "refill"
            refillCalls += mlsContext
            refillThrowable?.let { throw it }
            return refillResult
        }

        private suspend fun join(
            transactionContext: CryptoTransactionContext,
            conversationId: ConversationId,
        ): Either<CoreFailure, Unit> {
            callOrder += "join"
            joinCalls += ConversationCall(transactionContext, conversationId)
            joinThrowable?.let { throw it }
            return joinResult
        }

        fun withOrphanWelcome() {
            processWelcomeThrowable = CommonizedMLSException(
                MLSFailure.OrphanWelcome,
                IllegalStateException("key package already deleted locally")
            )
        }

        fun arrange(): Pair<Arrangement, MLSWelcomeEventHandler> = this to MLSWelcomeEventHandlerImpl(
            conversationRepository = repository,
            resolveOneOnOne = ::resolve,
            refillKeyPackages = ::refill,
            joinExistingMLSConversation = ::join,
            fetchConversationIfUnknown = ::fetch,
        )
    }

    private class MLSWelcomeRepositoryRecorder(
        private val callOrder: MutableList<String>,
    ) : MLSWelcomeEventRepository {
        var protocolResult: Either<CoreFailure, Conversation.ProtocolInfo> = Either.Right(PENDING_PROTOCOL_INFO)
        var updateResult: Either<StorageFailure, Unit> = Either.Right(Unit)
        var observeResults: List<Either<StorageFailure, ConversationDetails>> = listOf(Either.Right(CONVERSATION_GROUP))
        var protocolThrowable: Throwable? = null
        var updateThrowable: Throwable? = null
        var observeThrowable: Throwable? = null
        val protocolCalls = mutableListOf<ConversationId>()
        val updateCalls = mutableListOf<UpdateCall>()
        val observeCalls = mutableListOf<ConversationId>()
        var observeEmissionCount = 0

        override suspend fun getConversationProtocolInfo(
            conversationId: ConversationId
        ): Either<CoreFailure, Conversation.ProtocolInfo> {
            callOrder += "protocol"
            protocolCalls += conversationId
            protocolThrowable?.let { throw it }
            return protocolResult
        }

        override suspend fun updateConversationGroupState(
            groupID: GroupID,
            groupState: Conversation.ProtocolInfo.MLSCapable.GroupState,
        ): Either<StorageFailure, Unit> {
            callOrder += "update"
            updateCalls += UpdateCall(groupID, groupState)
            updateThrowable?.let { throw it }
            return updateResult
        }

        override suspend fun observeConversationDetailsById(
            conversationID: ConversationId
        ): Flow<Either<StorageFailure, ConversationDetails>> {
            callOrder += "observe"
            observeCalls += conversationID
            observeThrowable?.let { throw it }
            return flow {
                observeResults.forEach {
                    observeEmissionCount++
                    emit(it)
                }
            }
        }
    }

    private enum class ReturnedFailureStage(
        val failure: CoreFailure,
        val expectedOrder: List<String>,
    ) {
        FETCH(CoreFailure.Unknown(null), listOf("mls", "fetch")),
        UPDATE(StorageFailure.DataNotFound, listOf("mls", "fetch", "process", "update")),
        OBSERVE(StorageFailure.DataNotFound, listOf("mls", "fetch", "process", "update", "observe")),
        RESOLVE(
            NetworkFailure.NoNetworkConnection(null),
            listOf("mls", "fetch", "process", "update", "observe", "resolve")
        );

        fun configure(arrangement: Arrangement, failure: CoreFailure) {
            when (this) {
                FETCH -> arrangement.fetchResult = Either.Left(failure)
                UPDATE -> arrangement.repository.updateResult = Either.Left(failure as StorageFailure)
                OBSERVE -> arrangement.repository.observeResults = listOf(Either.Left(failure as StorageFailure))
                RESOLVE -> {
                    arrangement.repository.observeResults = listOf(Either.Right(CONVERSATION_ONE_ONE))
                    arrangement.resolveResult = Either.Left(failure)
                }
            }
        }
    }

    private enum class EscapingFailureStage(val expectedOrder: List<String>) {
        FETCH(listOf("mls", "fetch")),
        UPDATE(listOf("mls", "fetch", "process", "update")),
        OBSERVE(listOf("mls", "fetch", "process", "update", "observe")),
        RESOLVE(listOf("mls", "fetch", "process", "update", "observe", "resolve")),
        REFILL(listOf("mls", "fetch", "process", "update", "observe", "mls", "refill")),
        PROTOCOL(listOf("mls", "fetch", "process", "protocol")),
        JOIN(listOf("mls", "fetch", "process", "protocol", "join"));

        fun configure(arrangement: Arrangement, throwable: Throwable) {
            when (this) {
                FETCH -> arrangement.fetchThrowable = throwable
                UPDATE -> arrangement.repository.updateThrowable = throwable
                OBSERVE -> arrangement.repository.observeThrowable = throwable
                RESOLVE -> {
                    arrangement.repository.observeResults = listOf(Either.Right(CONVERSATION_ONE_ONE))
                    arrangement.resolveThrowable = throwable
                }
                REFILL -> arrangement.refillThrowable = throwable
                PROTOCOL -> {
                    arrangement.withOrphanWelcome()
                    arrangement.repository.protocolThrowable = throwable
                }
                JOIN -> {
                    arrangement.withOrphanWelcome()
                    arrangement.repository.protocolResult = Either.Right(PENDING_PROTOCOL_INFO)
                    arrangement.joinThrowable = throwable
                }
            }
        }
    }

    private enum class ProcessOrExistsStage(val expectedOrder: List<String>) {
        PROCESS(listOf("mls", "fetch", "process")),
        CONVERSATION_EXISTS(listOf("mls", "fetch", "process", "protocol", "conversation-exists")),
    }

    private data class ConversationCall(
        val transactionContext: CryptoTransactionContext,
        val conversationId: ConversationId,
    )

    private data class ResolveCall(
        val transactionContext: CryptoTransactionContext,
        val otherUser: OtherUser,
        val invalidateCurrentKnownProtocols: Boolean,
    )

    private data class UpdateCall(val groupId: GroupID, val groupState: Conversation.ProtocolInfo.MLSCapable.GroupState)

    private companion object {
        const val MLS_GROUP_ID: MLSGroupId = "test-mls-group-id"
        const val LOCAL_GROUP_ID: MLSGroupId = "local-mls-group-id"
        val CONVERSATION_ID = ConversationId("conversation-id", "wire.example")
        val SELF_USER_ID = UserId("self-user-id", "wire.example")
        val OTHER_USER_ID = UserId("other-user-id", "wire.example")
        val TEST_INSTANT = Instant.parse("2024-01-02T03:04:05Z")
        val WELCOME = "welcome".encodeToByteArray()
        val WELCOME_EVENT = Event.Conversation.MLSWelcome(
            id = "event-id",
            conversationId = CONVERSATION_ID,
            senderUserId = SELF_USER_ID,
            message = Base64.encode(WELCOME),
        )
        val PENDING_PROTOCOL_INFO = Conversation.ProtocolInfo.MLS(
            groupId = GroupID(LOCAL_GROUP_ID),
            groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN,
            epoch = 1uL,
            keyingMaterialLastUpdate = TEST_INSTANT,
            cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519,
        )
        val ESTABLISHED_PROTOCOL_INFO = PENDING_PROTOCOL_INFO.copy(
            groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED
        )
        val OTHER_USER = OtherUser(
            id = OTHER_USER_ID,
            name = "Other User",
            handle = "other-user",
            accentId = 1,
            teamId = null,
            connectionStatus = ConnectionState.ACCEPTED,
            previewPicture = null,
            completePicture = null,
            userType = UserTypeInfo.Regular(UserType.INTERNAL),
            availabilityStatus = UserAvailabilityStatus.NONE,
            supportedProtocols = setOf(SupportedProtocol.MLS),
            botService = null,
            deleted = false,
            defederated = false,
            isProteusVerified = false,
        )
        val BASE_CONVERSATION = Conversation(
            id = CONVERSATION_ID,
            name = "Conversation",
            type = Conversation.Type.Group.Regular,
            teamId = null,
            protocol = Conversation.ProtocolInfo.Proteus,
            mutedStatus = MutedConversationStatus.AllAllowed,
            removedBy = null,
            lastNotificationDate = null,
            lastModifiedDate = null,
            lastReadDate = TEST_INSTANT,
            access = emptyList(),
            accessRole = emptyList(),
            creatorId = null,
            receiptMode = Conversation.ReceiptMode.DISABLED,
            messageTimer = null,
            userMessageTimer = null,
            archived = false,
            archivedDateTime = null,
            mlsVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
            proteusVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED,
        )
        val CONVERSATION_GROUP = ConversationDetails.Group.Regular(
            conversation = BASE_CONVERSATION,
            isSelfUserMember = true,
            selfRole = Conversation.Member.Role.Member,
        )
        val CONVERSATION_ONE_ONE = ConversationDetails.OneOne(
            conversation = BASE_CONVERSATION.copy(type = Conversation.Type.OneOnOne),
            otherUser = OTHER_USER,
            userType = OTHER_USER.userType,
        )

        fun noConversationFailure(): NetworkFailure.ServerMiscommunication =
            NetworkFailure.ServerMiscommunication(
                KaliumException.InvalidRequestError(
                    GenericAPIErrorResponse(
                        code = 404,
                        message = "test error",
                        label = "no-conversation",
                    )
                )
            )

        fun arrange(configuration: Arrangement.() -> Unit = {}): Pair<Arrangement, MLSWelcomeEventHandler> =
            Arrangement().apply(configuration).arrange()
    }
}
