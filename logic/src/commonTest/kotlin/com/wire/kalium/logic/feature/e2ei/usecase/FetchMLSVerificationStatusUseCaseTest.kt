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
package com.wire.kalium.logic.feature.e2ei.usecase

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CredentialType
import com.wire.kalium.cryptography.CryptoCertificateStatus
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.E2EIConversationState
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.WireIdentity
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.mls.EpochChangesData
import com.wire.kalium.logic.data.conversation.mls.NameAndHandle
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.e2ei.usecase.FetchMLSVerificationStatusUseCaseTest.Arrangement.Companion.getMockedIdentity
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FetchMLSVerificationStatusUseCaseTest {

    @Test
    fun givenNotVerifiedConversation_whenNotVerifiedStatusComes_thenNothingChanged() = runTest {
        val conversationDetails = TestConversation.MLS_CONVERSATION
        val (arrangement, handler) = arrange {
            withGetMLSClientSuccess()
            withIsGroupVerified(E2EIConversationState.NOT_VERIFIED)
            withConversationByMLSGroupId(Either.Right(conversationDetails))
        }

        handler(TestConversation.GROUP_ID)
        advanceUntilIdle()

        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.updateMlsVerificationStatus(any(), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.persistMessageUseCase.invoke(any<Message.System>())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.setDegradedConversationNotifiedFlag(any(), any())
        }
    }

    @Test
    fun givenVerifiedConversation_whenNotVerifiedStatusComes_thenStatusSetToDegradedAndSystemMessageAdded() = runTest {
        val conversationDetails = TestConversation.GROUP().copy(
            mlsVerificationStatus = Conversation.VerificationStatus.VERIFIED
        )
        val (arrangement, handler) = arrange {
            withGetMLSClientSuccess()
            withIsGroupVerified(E2EIConversationState.NOT_VERIFIED)
            withConversationByMLSGroupId(Either.Right(conversationDetails))
        }

        handler(TestConversation.GROUP_ID)
        advanceUntilIdle()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.updateMlsVerificationStatus(
                verificationStatus = eq(Conversation.VerificationStatus.DEGRADED),
                conversationID = eq(conversationDetails.id)
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessageUseCase.invoke(any<Message.System>())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.setDegradedConversationNotifiedFlag(any(), eq(false))
        }
    }

    @Test
    fun givenDegradedConversation_whenNotVerifiedStatusComes_thenNothingChanged() = runTest {
        val conversationDetails = TestConversation.GROUP().copy(
            mlsVerificationStatus = Conversation.VerificationStatus.DEGRADED
        )

        val (arrangement, handler) = arrange {
            withGetMLSClientSuccess()
            withIsGroupVerified(E2EIConversationState.NOT_VERIFIED)
            withConversationByMLSGroupId(Either.Right(conversationDetails))
        }

        handler(TestConversation.GROUP_ID)
        advanceUntilIdle()

        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.updateMlsVerificationStatus(any(), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.persistMessageUseCase.invoke(any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.setDegradedConversationNotifiedFlag(any(), any())
        }
    }

    @Test
    fun givenDegradedConversation_whenVerifiedStatusComes_thenStatusUpdated() = runTest {

        val user1 = QualifiedID("user1", "domain1") to NameAndHandle("name1", "device1")
        val user2 = QualifiedID("user2", "domain2") to NameAndHandle("name2", "device2")

        val epochChangedData = EpochChangesData(
            conversationId = TestConversation.CONVERSATION.id,
            members = mapOf(user1, user2),
            mlsVerificationStatus = Conversation.VerificationStatus.DEGRADED
        )

        val ccMembersIdentity: Map<UserId, List<WireIdentity>> = mapOf(
            user1.first to listOf(
                getMockedIdentity(user1.first, user1.second)
            ),
            user2.first to listOf(
                getMockedIdentity(user2.first, user2.second)
            )
        )
        val (arrangement, handler) = arrange {
            withGetMLSClientSuccess()
            withIsGroupVerified(E2EIConversationState.VERIFIED)
            withSelectGroupStatusMembersNamesAndHandles(Either.Right(epochChangedData))
            withGetMembersIdentities(ccMembersIdentity)
        }

        handler(TestConversation.GROUP_ID)
        advanceUntilIdle()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.updateMlsVerificationStatus(
                eq(Conversation.VerificationStatus.VERIFIED),
                eq(epochChangedData.conversationId)
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessageUseCase.invoke(any<Message.System>())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.setDegradedConversationNotifiedFlag(any(), eq(true))
        }
    }

    @Test
    fun givenVerifiedConversation_whenVerifiedStatusComesAndUserNamesDivergeFromCC_thenStatusUpdatedToDegraded() = runTest {

        val user1 = QualifiedID("user1", "domain1") to NameAndHandle("name1", "device1")
        val user2 = QualifiedID("user2", "domain2") to NameAndHandle("name2", "device2")

        val epochChangedData = EpochChangesData(
            conversationId = TestConversation.CONVERSATION.id,
            members = mapOf(user1, user2),
            mlsVerificationStatus = Conversation.VerificationStatus.VERIFIED
        )

        val ccMembersIdentity: Map<UserId, List<WireIdentity>> = mapOf(
            user1.first to listOf(
                getMockedIdentity(user1.first, user1.second, CryptoCertificateStatus.REVOKED)
            ),
            user2.first to listOf(
                getMockedIdentity(user2.first, user2.second)
            )
        )
        val (arrangement, handler) = arrange {
            withGetMLSClientSuccess()
            withIsGroupVerified(E2EIConversationState.VERIFIED)
            withSelectGroupStatusMembersNamesAndHandles(Either.Right(epochChangedData))
            withGetMembersIdentities(ccMembersIdentity)
        }

        handler(TestConversation.GROUP_ID)
        advanceUntilIdle()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.updateMlsVerificationStatus(
                eq(Conversation.VerificationStatus.DEGRADED),
                eq(epochChangedData.conversationId)
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessageUseCase.invoke(any<Message.System>())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.setDegradedConversationNotifiedFlag(any(), eq(false))
        }
    }

    private suspend fun arrange(block: suspend Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: suspend Arrangement.() -> Unit
    ) {

        val mlsClientProvider: MLSClientProvider = mock()
        val mlsClient: MLSClient = mock()
        val mlsConversationRepository: MLSConversationRepository = mock()
        val conversationRepository: ConversationRepository = mock()
        val persistMessageUseCase: PersistMessageUseCase = mock()
        val userRepository: UserRepository = mock()

        suspend fun withGetMLSClientSuccess() {
            everySuspend { mlsClientProvider.getMLSClient(any()) } returns Either.Right(mlsClient)
        }

        suspend fun withIsGroupVerified(result: E2EIConversationState) {
            everySuspend {
                mlsClient.getGroupState(any())
            } returns result
        }

        suspend fun withGetMembersIdentities(result: Map<UserId, List<WireIdentity>>) {
            everySuspend {
                mlsConversationRepository.getMembersIdentities(eq(mlsClient), any(), any())
            } returns Either.Right(result)
        }

        suspend fun withUpdateVerificationStatus(result: Either<CoreFailure, Unit>) {
            everySuspend { conversationRepository.updateMlsVerificationStatus(any(), any()) } returns result
        }

        suspend fun withConversationByMLSGroupId(result: Either<CoreFailure, Conversation>) {
            everySuspend { conversationRepository.getConversationByMLSGroupId(any()) } returns result
        }

        suspend fun withPersistingMessage(result: Either<CoreFailure, Unit>) {
            everySuspend { persistMessageUseCase.invoke(any()) } returns result
        }

        suspend fun withSetDegradedConversationNotifiedFlag(result: Either<CoreFailure, Unit>) {
            everySuspend { conversationRepository.setDegradedConversationNotifiedFlag(any(), any()) } returns result
        }

        suspend fun withSelectGroupStatusMembersNamesAndHandles(result: Either<StorageFailure, EpochChangesData>) {
            everySuspend { conversationRepository.getGroupStatusMembersNamesAndHandles(any()) } returns result
        }

        suspend inline fun arrange() = let {
            withUpdateVerificationStatus(Either.Right(Unit))
            withPersistingMessage(Either.Right(Unit))
            withSetDegradedConversationNotifiedFlag(Either.Right(Unit))
            block()
            this to FetchMLSVerificationStatusUseCaseImpl(
                mlsClientProvider = mlsClientProvider,
                mlsConversationRepository = mlsConversationRepository,
                conversationRepository = conversationRepository,
                persistMessage = persistMessageUseCase,
                selfUserId = TestUser.USER_ID,
                kaliumLogger = kaliumLogger,
                userRepository = userRepository
            )
        }

        companion object {
            fun getMockedIdentity(
                userId: QualifiedID,
                nameAndHandle: NameAndHandle,
                status: CryptoCertificateStatus = CryptoCertificateStatus.VALID
            ) = WireIdentity(
                CryptoQualifiedClientId(
                    value = userId.value,
                    userId = userId.toCrypto()
                ),
                status,
                thumbprint = "thumbprint",
                credentialType = CredentialType.X509,
                x509Identity = WireIdentity.X509Identity(
                    WireIdentity.Handle(
                        scheme = "wireapp",
                        handle = nameAndHandle.handle!!,
                        domain = userId.domain
                    ),
                    displayName = nameAndHandle.name!!,
                    domain = userId.domain,
                    certificate = "cert1",
                    serialNumber = "serial1",
                    notBefore = Instant.DISTANT_PAST.epochSeconds,
                    notAfter = Instant.DISTANT_FUTURE.epochSeconds
                )
            )
        }
    }
}
