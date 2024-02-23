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
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationsUseCaseImpl
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.util.DateTimeUtil
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class JoinExistingMLSConversationsUseCaseTest {

    @Test
    fun givenMLSSupportIsDisabled_whenInvokingUseCase_ThenRequestToJoinConversationIsNotCalled() =
        runTest {
            val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement()
                .withIsMLSSupported(false)
                .withJoinExistingMLSConversationSuccessful()
                .withGetConversationsByGroupStateSuccessful()
                .arrange()

            joinExistingMLSConversationsUseCase().shouldSucceed()

            verify(arrangement.mlsConversationRepository)
                .suspendFunction(arrangement.mlsConversationRepository::joinGroupByExternalCommit)
                .with(eq(Arrangement.MLS_CONVERSATION1), anything())
                .wasNotInvoked()

            verify(arrangement.joinExistingMLSConversationUseCase)
                .suspendFunction(arrangement.joinExistingMLSConversationUseCase::invoke)
                .with(anything())
                .wasNotInvoked()
        }

    @Test
    fun givenNoMLSClientIsRegistered_whenInvokingUseCase_ThenRequestToJoinConversationIsNotCalled() =
        runTest {
            val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(false)
                .withJoinExistingMLSConversationSuccessful()
                .withGetConversationsByGroupStateSuccessful()
                .arrange()

            joinExistingMLSConversationsUseCase().shouldSucceed()

            verify(arrangement.mlsConversationRepository)
                .suspendFunction(arrangement.mlsConversationRepository::joinGroupByExternalCommit)
                .with(eq(Arrangement.MLS_CONVERSATION1), anything())
                .wasNotInvoked()

            verify(arrangement.joinExistingMLSConversationUseCase)
                .suspendFunction(arrangement.joinExistingMLSConversationUseCase::invoke)
                .with(anything())
                .wasNotInvoked()
        }

    @Test
    fun givenExistingConversations_whenInvokingUseCase_ThenRequestToJoinConversationIsCalledForAllConversations() =
        runTest {
            val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(true)
                .withJoinExistingMLSConversationSuccessful()
                .withGetConversationsByGroupStateSuccessful()
                .arrange()

            joinExistingMLSConversationsUseCase().shouldSucceed()

            verify(arrangement.joinExistingMLSConversationUseCase)
                .suspendFunction(arrangement.joinExistingMLSConversationUseCase::invoke)
                .with(anything())
                .wasInvoked(twice)
        }

    @Test
    fun givenNoKeyPackagesAvailable_WhenJoinExistingMLSConversationUseCase_ThenReturnUnit() =
        runTest {
            val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(true)
                .withGetConversationsByGroupStateSuccessful()
                .withNoKeyPackagesAvailable()
                .arrange()

            joinExistingMLSConversationsUseCase().shouldSucceed()

            verify(arrangement.joinExistingMLSConversationUseCase)
                .suspendFunction(arrangement.joinExistingMLSConversationUseCase::invoke)
                .with(anything())
                .wasInvoked(twice)
        }

    @Test
    fun givenNetworkFailure_WhenJoinExistingMLSConversationUseCase_ThenPropagateFailure() = runTest {
        val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement()
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withGetConversationsByGroupStateSuccessful()
            .withJoinExistingMLSConversationNetworkFailure()
            .arrange()

        joinExistingMLSConversationsUseCase().shouldFail {
            assertIs<NetworkFailure>(it)
        }
        verify(arrangement.joinExistingMLSConversationUseCase)
            .suspendFunction(arrangement.joinExistingMLSConversationUseCase::invoke)
            .with(anything())
            .wasInvoked(twice)
    }

    @Test
    fun givenOtherFailure_WhenJoinExistingMLSConversationUseCase_ThenReturnUnit() = runTest {
        val (arrangement, joinExistingMLSConversationsUseCase) = Arrangement()
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withGetConversationsByGroupStateSuccessful()
            .withJoinExistingMLSConversationFailure()
            .arrange()

        joinExistingMLSConversationsUseCase().shouldSucceed()

        verify(arrangement.joinExistingMLSConversationUseCase)
            .suspendFunction(arrangement.joinExistingMLSConversationUseCase::invoke)
            .with(anything())
            .wasInvoked(twice)
    }

    private class Arrangement {

        @Mock
        val featureSupport = mock(classOf<FeatureSupport>())

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val mlsConversationRepository = mock(classOf<MLSConversationRepository>())

        @Mock
        val joinExistingMLSConversationUseCase = mock(classOf<JoinExistingMLSConversationUseCase>())

        fun arrange() = this to JoinExistingMLSConversationsUseCaseImpl(
            featureSupport,
            clientRepository,
            conversationRepository,
            joinExistingMLSConversationUseCase
        )

        @Suppress("MaxLineLength")
        fun withGetConversationsByGroupStateSuccessful(
            conversations: List<Conversation> = listOf(
                MLS_CONVERSATION1,
                MLS_CONVERSATION2
            )
        ) =
            apply {
                given(conversationRepository)
                    .suspendFunction(conversationRepository::getConversationsByGroupState)
                    .whenInvokedWith(anything())
                    .then { Either.Right(conversations) }
            }

        fun withJoinExistingMLSConversationSuccessful() = apply {
            given(joinExistingMLSConversationUseCase)
                .suspendFunction(joinExistingMLSConversationUseCase::invoke)
                .whenInvokedWith(anything())
                .then { Either.Right(Unit) }
        }

        fun withJoinExistingMLSConversationNetworkFailure() = apply {
            given(joinExistingMLSConversationUseCase)
                .suspendFunction(joinExistingMLSConversationUseCase::invoke)
                .whenInvokedWith(anything())
                .then { Either.Left(NetworkFailure.NoNetworkConnection(null)) }
        }

        fun withJoinExistingMLSConversationFailure() = apply {
            given(joinExistingMLSConversationUseCase)
                .suspendFunction(joinExistingMLSConversationUseCase::invoke)
                .whenInvokedWith(anything())
                .then { Either.Left(CoreFailure.NotSupportedByProteus) }
        }

        fun withNoKeyPackagesAvailable() = apply {
            given(joinExistingMLSConversationUseCase)
                .suspendFunction(joinExistingMLSConversationUseCase::invoke)
                .whenInvokedWith(anything())
                .then { Either.Left(CoreFailure.MissingKeyPackages(setOf())) }
        }


        fun withIsMLSSupported(supported: Boolean) = apply {
            given(featureSupport)
                .invocation { featureSupport.isMLSSupported }
                .thenReturn(supported)
        }

        fun withHasRegisteredMLSClient(result: Boolean) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::hasRegisteredMLSClient)
                .whenInvoked()
                .thenReturn(Either.Right(result))
        }

        companion object {
            val GROUP_ID1 = GroupID("group1")
            val GROUP_ID2 = GroupID("group2")

            val MLS_CONVERSATION1 = TestConversation.GROUP(
                Conversation.ProtocolInfo.MLS(
                    GROUP_ID1,
                    Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN,
                    epoch = 1UL,
                    keyingMaterialLastUpdate = DateTimeUtil.currentInstant(),
                    cipherSuite = Conversation.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
                )
            ).copy(id = ConversationId("id1", "domain"))

            val MLS_CONVERSATION2 = TestConversation.GROUP(
                Conversation.ProtocolInfo.MLS(
                    GROUP_ID2,
                    Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN,
                    epoch = 1UL,
                    keyingMaterialLastUpdate = DateTimeUtil.currentInstant(),
                    cipherSuite = Conversation.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
                )
            ).copy(id = ConversationId("id2", "domain"))
        }
    }
}
