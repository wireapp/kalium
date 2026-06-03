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
package com.wire.kalium.logic.feature.conversation.mls

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.logic.feature.protocol.OneOnOneProtocolSelector
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any as mokkeryAny
import dev.mokkery.matcher.eq as mokkeryEq
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class OneOnOneResolverTest {

    @Test
    fun givenListOneOnOneUsers_whenResolveAllOneOnOneConversations_thenResolveOneOnOneForEachUser() = runTest {
        val oneOnOneUsers = listOf(TestUser.OTHER.copy(id = TestUser.OTHER_USER_ID), TestUser.OTHER.copy(id = TestUser.OTHER_USER_ID_2))
        val (arrangement, resolver) = arrange {
            withGetUsersWithOneOnOneConversationReturning(oneOnOneUsers)
            withGetProtocolForUser(Either.Right(SupportedProtocol.MLS))
            withMigrateToMLSReturns(Either.Right(TestConversation.ID))
        }

        resolver.resolveAllOneOnOneConversations(arrangement.transactionContext).shouldSucceed()

        verifySuspend(VerifyMode.exactly(2)) {
            arrangement.oneOnOneProtocolSelector.getProtocolForUser(matching { it in oneOnOneUsers.map(OtherUser::id) })
        }
    }

    @Test
    fun givenListOneOnOneUsersAndSynchronizeUsers_whenResolveAllOneOnOneConversations_thenShouldFetchAllUserDetailsAtOnce() = runTest {
        val oneOnOneUsers = listOf(
            TestUser.OTHER.copy(id = TestUser.OTHER_USER_ID),
            TestUser.OTHER.copy(id = TestUser.OTHER_USER_ID_2)
        )
        val (arrangement, resolver) = arrange {
            withFetchAllOtherUsersReturning(Either.Right(Unit))
            withGetUsersWithOneOnOneConversationReturning(oneOnOneUsers)
            withGetProtocolForUser(Either.Right(SupportedProtocol.MLS))
            withMigrateToMLSReturns(Either.Right(TestConversation.ID))
        }

        resolver.resolveAllOneOnOneConversations(arrangement.transactionContext, true).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.fetchAllOtherUsers()
        }

        verifySuspend(VerifyMode.not) {
            arrangement.userRepository.fetchUserInfo(mokkeryAny())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.userRepository.fetchUsersByIds(mokkeryAny())
        }
    }

    @Test
    fun givenSingleOneOnOneUserIdAndSynchronizeUser_whenResolveAllOneOnOneConversations_thenShouldFetchUserDetailsOnce() = runTest {
        val oneOnOneUser = TestUser.OTHER.copy(id = TestUser.OTHER_USER_ID)
        val (arrangement, resolver) = arrange {
            withFetchUsersByIdReturning(Either.Right(true))
            withGetProtocolForUser(Either.Right(SupportedProtocol.MLS))
            withMigrateToMLSReturns(Either.Right(TestConversation.ID))
        }

        resolver.resolveOneOnOneConversationWithUser(arrangement.transactionContext, oneOnOneUser, true).shouldSucceed()

        verifySuspend(VerifyMode.not) {
            arrangement.userRepository.fetchAllOtherUsers()
        }

        verifySuspend(VerifyMode.not) {
            arrangement.userRepository.fetchUserInfo(mokkeryAny())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.fetchUsersByIds(mokkeryEq(setOf(oneOnOneUser.id)))
        }
    }

    @Test
    fun givenSingleOneOnOneUserIdAndSynchronizeUserFails_whenResolveAllOneOnOneConversations_thenShouldNotPropagateFailure() = runTest {
        val oneOnOneUser = TestUser.OTHER.copy(id = TestUser.OTHER_USER_ID)
        val (arrangement, resolver) = arrange {
            withFetchUsersByIdReturning(Either.Right(true))
            withGetProtocolForUser(Either.Right(SupportedProtocol.MLS))
            withMigrateToMLSReturns(Either.Right(TestConversation.ID))
        }

        resolver.resolveOneOnOneConversationWithUser(arrangement.transactionContext, oneOnOneUser, true)
            .shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.fetchUsersByIds(mokkeryEq(setOf(oneOnOneUser.id)))
        }
    }

    @Test
    fun givenSingleOneOnOneUserAndSynchronizeUsers_whenResolveAllOneOnOneConversations_thenShouldFetchUserDetailsOnce() = runTest {
        val oneOnOneUser = TestUser.OTHER.copy(id = TestUser.OTHER_USER_ID)
        val (arrangement, resolver) = arrange {
            withGetKnownUserReturning(flowOf(oneOnOneUser))
            withFetchUsersByIdReturning(Either.Right(true))
            withGetProtocolForUser(Either.Right(SupportedProtocol.MLS))
            withMigrateToMLSReturns(Either.Right(TestConversation.ID))
        }

        resolver.resolveOneOnOneConversationWithUserId(arrangement.transactionContext, oneOnOneUser.id, true).shouldSucceed()

        verifySuspend(VerifyMode.not) {
            arrangement.userRepository.fetchAllOtherUsers()
        }

        verifySuspend(VerifyMode.not) {
            arrangement.userRepository.fetchUserInfo(mokkeryAny())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.fetchUsersByIds(mokkeryEq(setOf(oneOnOneUser.id)))
        }
    }

    @Test
    fun givenSingleOneOnOneUserAndSynchronizeUserFails_whenResolveAllOneOnOneConversations_thenShouldNotPropagateFailure() = runTest {
        val oneOnOneUser = TestUser.OTHER.copy(id = TestUser.OTHER_USER_ID)
        val (arrangement, resolver) = arrange {
            withFetchUsersByIdReturning(Either.Left(CoreFailure.Unknown(null)))
            withGetKnownUserReturning(flowOf(oneOnOneUser))
            withGetProtocolForUser(Either.Right(SupportedProtocol.MLS))
            withMigrateToMLSReturns(Either.Right(TestConversation.ID))
        }

        resolver.resolveOneOnOneConversationWithUserId(arrangement.transactionContext, oneOnOneUser.id, true)
            .shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.fetchUsersByIds(mokkeryEq(setOf(oneOnOneUser.id)))
        }
    }

    @Test
    fun givenResolvingOneConversationFails_whenResolveAllOneOnOneConversations_thenTheWholeOperationFails() = runTest {
        val oneOnOneUsers = listOf(TestUser.OTHER.copy(id = TestUser.OTHER_USER_ID), TestUser.OTHER.copy(id = TestUser.OTHER_USER_ID_2))
        val (arrangement, resolver) = arrange {
            withGetUsersWithOneOnOneConversationReturning(oneOnOneUsers)
            withGetProtocolForUser(Either.Right(SupportedProtocol.MLS))
            withMigrateToMLSReturns(Either.Right(TestConversation.ID))
        }

        everySuspend {
            arrangement.oneOnOneMigrator.migrateToMLS(mokkeryAny(), mokkeryEq(oneOnOneUsers.last()), mokkeryAny())
        } returns Either.Left(CoreFailure.Unknown(null))

        resolver.resolveAllOneOnOneConversations(arrangement.transactionContext).shouldFail()
    }

    @Test
    fun givenProtocolResolvesToMLS_whenResolveOneOnOneConversationWithUser_thenMigrateToMLS() = runTest {
        val (arrangement, resolver) = arrange {
            withGetProtocolForUser(Either.Right(SupportedProtocol.MLS))
            withMigrateToMLSReturns(Either.Right(TestConversation.ID))
        }

        resolver.resolveOneOnOneConversationWithUser(arrangement.transactionContext, OTHER_USER, false).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.oneOnOneMigrator.migrateToMLS(mokkeryAny(), mokkeryEq(OTHER_USER), mokkeryEq(true))
        }
    }

    @Test
    fun givenExternalCommitJoinDisabled_whenResolveAllOneOnOneConversations_thenForwardFlagToMigrateToMls() = runTest {
        val oneOnOneUsers = listOf(TestUser.OTHER.copy(id = TestUser.OTHER_USER_ID))
        val (arrangement, resolver) = arrange {
            withGetUsersWithOneOnOneConversationReturning(oneOnOneUsers)
            withGetProtocolForUser(Either.Right(SupportedProtocol.MLS))
            withMigrateToMLSReturns(Either.Right(TestConversation.ID))
        }

        resolver.resolveAllOneOnOneConversations(
            transactionContext = arrangement.transactionContext,
            allowJoinByExternalCommit = false
        ).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.oneOnOneMigrator.migrateToMLS(mokkeryAny(), mokkeryEq(oneOnOneUsers.first()), mokkeryEq(false))
        }
    }

    @Test
    fun givenProtocolResolvesToProteus_whenResolveOneOnOneConversationWithUser_thenMigrateToProteus() = runTest {
        val (arrangement, resolver) = arrange {
            withGetProtocolForUser(Either.Right(SupportedProtocol.PROTEUS))
            withMigrateToProteusReturns(Either.Right(TestConversation.ID))
        }

        resolver.resolveOneOnOneConversationWithUser(arrangement.transactionContext, OTHER_USER, false).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.oneOnOneMigrator.migrateToProteus(mokkeryEq(OTHER_USER))
        }
    }

    @Test
    fun givenProtocolResolvesToOtherNeedToUpdate_whenResolveOneOnOneConversationWithUser_thenMigrateExistingToProteus() = runTest {
        val (arrangement, resolver) = arrange {
            withGetProtocolForUser(CoreFailure.NoCommonProtocolFound.OtherNeedToUpdate.left())
            withMigrateExistingToProteusReturns(TestConversation.ID.right())
        }

        resolver.resolveOneOnOneConversationWithUser(arrangement.transactionContext, OTHER_USER, false).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.oneOnOneMigrator.migrateExistingProteus(mokkeryEq(OTHER_USER))
        }
    }

    @Test
    fun givenThrottleError_whenResolveAllOneOnOneConversations_thenRetriesBeforePropagatingFailure() = runTest {
        val oneOnOneUsers = listOf(TestUser.OTHER.copy(id = TestUser.OTHER_USER_ID))
        val (arrangement, resolver) = arrange(maxThrottleRetries = 2, throttleRetryDelayMs = 1) {
            withGetUsersWithOneOnOneConversationReturning(oneOnOneUsers)
            withGetProtocolForUser(
                Either.Left(
                    NetworkFailure.ServerMiscommunication(
                        KaliumException.InvalidRequestError(
                            GenericAPIErrorResponse(420, "", "Unknown Status Code")
                        )
                    )
                )
            )
        }

        resolver.resolveAllOneOnOneConversations(arrangement.transactionContext).shouldFail {
            assertIs<NetworkFailure.ServerMiscommunication>(it)
        }

        verifySuspend(VerifyMode.exactly(3)) {
            arrangement.oneOnOneProtocolSelector.getProtocolForUser(mokkeryAny())
        }
    }

    @Test
    fun givenThrottleErrorThenSuccess_whenResolveAllOneOnOneConversations_thenSucceeds() = runTest {
        val oneOnOneUsers = listOf(TestUser.OTHER.copy(id = TestUser.OTHER_USER_ID))
        val throttleFailure = Either.Left(
            NetworkFailure.ServerMiscommunication(
                KaliumException.InvalidRequestError(
                    GenericAPIErrorResponse(420, "", "Unknown Status Code")
                )
            )
        )
        val (arrangement, resolver) = arrange(maxThrottleRetries = 3, throttleRetryDelayMs = 1) {
            withGetUsersWithOneOnOneConversationReturning(oneOnOneUsers)
            withMigrateToMLSReturns(Either.Right(TestConversation.ID))
        }

        var attempts = 0
        everySuspend {
            arrangement.oneOnOneProtocolSelector.getProtocolForUser(mokkeryAny())
        } calls {
            attempts += 1
            if (attempts == 1) throttleFailure else Either.Right(SupportedProtocol.MLS)
        }

        resolver.resolveAllOneOnOneConversations(arrangement.transactionContext).shouldSucceed()

        verifySuspend(VerifyMode.exactly(2)) {
            arrangement.oneOnOneProtocolSelector.getProtocolForUser(mokkeryAny())
        }
    }

    @Test
    fun givenPeerIsDeletedAndOneOnOneIsMls_whenResolving_thenWipesMlsGroupAndKeepsLocalConversation() = runTest {
        val deletedPeer = TestUser.OTHER.copy(deleted = true)
        val convId = TestConversation.ID
        val (arrangement, resolver) = arrange {
            withGetOneOnOnConversationIdReturning(Either.Right(convId))
            withGetConversationProtocolInfoReturning(Either.Right(TestConversation.MLS_PROTOCOL_INFO))
            withLeaveGroupReturning(Either.Right(Unit))
        }

        resolver.resolveOneOnOneConversationWithUser(arrangement.transactionContext, deletedPeer, false).shouldSucceed {
            kotlin.test.assertEquals(convId, it)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.leaveGroup(mokkeryAny(), mokkeryEq(TestConversation.MLS_PROTOCOL_INFO.groupId))
        }
        verifySuspend(VerifyMode.not) { arrangement.conversationRepository.deleteConversationLocally(mokkeryAny()) }
        verifySuspend(VerifyMode.not) { arrangement.oneOnOneProtocolSelector.getProtocolForUser(mokkeryAny()) }
        verifySuspend(VerifyMode.not) { arrangement.oneOnOneMigrator.migrateToMLS(mokkeryAny(), mokkeryAny(), mokkeryAny()) }
        verifySuspend(VerifyMode.not) { arrangement.oneOnOneMigrator.migrateToProteus(mokkeryAny()) }
        verifySuspend(VerifyMode.not) { arrangement.userRepository.fetchUsersByIds(mokkeryAny()) }
    }

    @Test
    fun givenPeerIsDeletedAndOneOnOneIsProteus_whenResolving_thenDoesNotTouchCryptoAndKeepsLocalConversation() = runTest {
        val deletedPeer = TestUser.OTHER.copy(deleted = true)
        val convId = TestConversation.ID
        val (arrangement, resolver) = arrange {
            withGetOneOnOnConversationIdReturning(Either.Right(convId))
            withGetConversationProtocolInfoReturning(Either.Right(TestConversation.PROTEUS_PROTOCOL_INFO))
        }

        resolver.resolveOneOnOneConversationWithUser(arrangement.transactionContext, deletedPeer, false).shouldSucceed {
            kotlin.test.assertEquals(convId, it)
        }

        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.leaveGroup(mokkeryAny(), mokkeryAny())
        }
        verifySuspend(VerifyMode.not) { arrangement.conversationRepository.deleteConversationLocally(mokkeryAny()) }
        verifySuspend(VerifyMode.not) { arrangement.oneOnOneMigrator.migrateToMLS(mokkeryAny(), mokkeryAny(), mokkeryAny()) }
    }

    @Test
    fun givenPeerIsDeletedAndNoActive1on1_whenResolving_thenReturnsLeftAndDoesNothing() = runTest {
        val deletedPeer = TestUser.OTHER.copy(deleted = true)
        val (arrangement, resolver) = arrange {
            withGetOneOnOnConversationIdReturning(Either.Left(StorageFailure.DataNotFound))
        }

        resolver.resolveOneOnOneConversationWithUser(arrangement.transactionContext, deletedPeer, false).shouldFail()

        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.leaveGroup(mokkeryAny(), mokkeryAny())
        }
        verifySuspend(VerifyMode.not) { arrangement.oneOnOneProtocolSelector.getProtocolForUser(mokkeryAny()) }
        verifySuspend(VerifyMode.not) { arrangement.oneOnOneMigrator.migrateToMLS(mokkeryAny(), mokkeryAny(), mokkeryAny()) }
    }

    @Test
    fun givenPeerIsDeletedAndMlsGroupAlreadyWiped_whenResolving_thenStillReturnsSuccess() = runTest {
        val deletedPeer = TestUser.OTHER.copy(deleted = true)
        val convId = TestConversation.ID
        val (arrangement, resolver) = arrange {
            withGetOneOnOnConversationIdReturning(Either.Right(convId))
            withGetConversationProtocolInfoReturning(Either.Right(TestConversation.MLS_PROTOCOL_INFO))
            withLeaveGroupReturning(Either.Left(MLSFailure.Generic(IllegalStateException("group not found"))))
        }

        resolver.resolveOneOnOneConversationWithUser(arrangement.transactionContext, deletedPeer, false).shouldSucceed {
            kotlin.test.assertEquals(convId, it)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.leaveGroup(mokkeryAny(), mokkeryAny())
        }
        verifySuspend(VerifyMode.not) { arrangement.oneOnOneMigrator.migrateToMLS(mokkeryAny(), mokkeryAny(), mokkeryAny()) }
    }

    @Test
    fun givenPeerIsDeletedAndScheduledResolveById_whenResolving_thenShortCircuitsViaCleanupPath() = runTest {
        val deletedPeer = TestUser.OTHER.copy(deleted = true)
        val convId = TestConversation.ID
        val (arrangement, resolver) = arrange {
            withGetKnownUserReturning(flowOf(deletedPeer))
            withFetchUsersByIdReturning(Either.Right(true))
            withGetOneOnOnConversationIdReturning(Either.Right(convId))
            withGetConversationProtocolInfoReturning(Either.Right(TestConversation.MLS_PROTOCOL_INFO))
            withLeaveGroupReturning(Either.Right(Unit))
        }

        resolver.resolveOneOnOneConversationWithUserId(arrangement.transactionContext, deletedPeer.id, true).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.leaveGroup(mokkeryAny(), mokkeryAny())
        }
        verifySuspend(VerifyMode.not) { arrangement.oneOnOneProtocolSelector.getProtocolForUser(mokkeryAny()) }
    }

    private class Arrangement(
        private val maxConcurrentResolutions: Int = 4,
        private val maxThrottleRetries: Int = 3,
        private val throttleRetryDelayMs: Long = 250,
        private val block: suspend Arrangement.() -> Unit,
    ) :
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val userRepository = mock<UserRepository>(mode = MockMode.autoUnit)
        val oneOnOneProtocolSelector = mock<OneOnOneProtocolSelector>(mode = MockMode.autoUnit)
        val oneOnOneMigrator = mock<OneOnOneMigrator>(mode = MockMode.autoUnit)
        val incrementalSyncRepository = mock<IncrementalSyncRepository>(mode = MockMode.autoUnit)
        val pendingActionsRepository = mock<PendingActionsRepository>(mode = MockMode.autoUnit)
        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val mlsConversationRepository = mock<MLSConversationRepository>(mode = MockMode.autoUnit)
        fun arrange() = run {
            runBlocking {
                everySuspend { pendingActionsRepository.enqueuePendingOneOnOneResolution(mokkeryAny()) } returns Unit
            }
            runBlocking { block() }
            this@Arrangement to OneOnOneResolverImpl(
                userRepository = userRepository,
                oneOnOneProtocolSelector = oneOnOneProtocolSelector,
                oneOnOneMigrator = oneOnOneMigrator,
                incrementalSyncRepository = incrementalSyncRepository,
                pendingActionsRepository = pendingActionsRepository,
                conversationRepository = conversationRepository,
                mlsConversationRepository = mlsConversationRepository,
                maxConcurrentResolutions = maxConcurrentResolutions,
                maxThrottleRetries = maxThrottleRetries,
                throttleRetryDelayMs = throttleRetryDelayMs,
            )
        }

        suspend fun withGetOneOnOnConversationIdReturning(result: Either<StorageFailure, ConversationId>) {
            everySuspend { userRepository.getOneOnOnConversationId(mokkeryAny()) } returns result
        }

        suspend fun withGetConversationProtocolInfoReturning(result: Either<StorageFailure, Conversation.ProtocolInfo>) {
            everySuspend { conversationRepository.getConversationProtocolInfo(mokkeryAny()) } returns result
        }

        suspend fun withLeaveGroupReturning(result: Either<CoreFailure, Unit>) {
            everySuspend { mlsConversationRepository.leaveGroup(mokkeryAny(), mokkeryAny()) } returns result
        }

        suspend fun withGetUsersWithOneOnOneConversationReturning(result: List<OtherUser>) {
            everySuspend { userRepository.getUsersWithOneOnOneConversation() } returns result
        }

        suspend fun withFetchAllOtherUsersReturning(result: Either<CoreFailure, Unit>) {
            everySuspend { userRepository.fetchAllOtherUsers() } returns result
        }

        suspend fun withFetchUsersByIdReturning(result: Either<CoreFailure, Boolean>) {
            everySuspend { userRepository.fetchUsersByIds(mokkeryAny()) } returns result
        }

        suspend fun withGetKnownUserReturning(result: Flow<OtherUser?>) {
            everySuspend { userRepository.getKnownUser(mokkeryAny()) } returns result
        }

        suspend fun withGetProtocolForUser(result: Either<CoreFailure, SupportedProtocol>) {
            everySuspend { oneOnOneProtocolSelector.getProtocolForUser(mokkeryAny()) } returns result
        }

        suspend fun withMigrateToMLSReturns(result: Either<CoreFailure, com.wire.kalium.logic.data.id.ConversationId>) {
            everySuspend { oneOnOneMigrator.migrateToMLS(mokkeryAny(), mokkeryAny(), mokkeryAny()) } returns result
        }

        suspend fun withMigrateToProteusReturns(result: Either<CoreFailure, com.wire.kalium.logic.data.id.ConversationId>) {
            everySuspend { oneOnOneMigrator.migrateToProteus(mokkeryAny()) } returns result
        }

        suspend fun withMigrateExistingToProteusReturns(result: Either<CoreFailure, com.wire.kalium.logic.data.id.ConversationId>) {
            everySuspend { oneOnOneMigrator.migrateExistingProteus(mokkeryAny()) } returns result
        }

        fun withIncrementalSyncState(flow: Flow<IncrementalSyncStatus>) {
            dev.mokkery.every { incrementalSyncRepository.incrementalSyncState } returns flow
        }
    }

    private companion object {
        fun arrange(
            maxConcurrentResolutions: Int = 4,
            maxThrottleRetries: Int = 3,
            throttleRetryDelayMs: Long = 250,
            configuration: suspend Arrangement.() -> Unit,
        ) = Arrangement(
            maxConcurrentResolutions = maxConcurrentResolutions,
            maxThrottleRetries = maxThrottleRetries,
            throttleRetryDelayMs = throttleRetryDelayMs,
            block = configuration,
        ).arrange()

        val OTHER_USER = TestUser.OTHER
    }
}
