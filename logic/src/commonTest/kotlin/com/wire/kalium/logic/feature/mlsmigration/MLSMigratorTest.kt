/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.mlsmigration

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.authenticated.conversation.UpdateConversationProtocolResponse
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationProtocolDTO
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MLSMigratorTest {

    @Test
    fun givenTeamConversation_whenMigrating_thenProtocolIsUpdatedToMixedAndGroupIsEstablished() = runTest {
        val conversation = TestConversation.CONVERSATION.copy(
            type = Conversation.Type.GROUP,
            teamId = TestTeam.TEAM_ID
        )

        val (arrangement, migrator) = Arrangement()
            .withGetConversationListReturning(listOf(conversation))
            .withUpdateProtocolReturns(Arrangement.UPDATE_PROTOCOL_SUCCESS)
            .withFetchConversationSucceeding()
            .withGetConversationProtocolInfoReturning(Arrangement.MIXED_PROTOCOL_INFO)
            .withEstablishGroupSucceeds()
            .withGetConversationMembersReturning(Arrangement.MEMBERS)
            .withAddMembersSucceeds()
            .arrange()

        migrator.migrateProteusConversations()

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::updateProtocol)
            .with(eq(conversation.id.toApi()), eq(ConvProtocol.MIXED))
            .wasInvoked(once)

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::establishMLSGroup)
            .with(eq(Arrangement.MIXED_PROTOCOL_INFO.groupId), eq(emptyList()))

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::addMemberToMLSGroup)
            .with(eq(Arrangement.MIXED_PROTOCOL_INFO.groupId), eq(Arrangement.MEMBERS))
    }

    @Test
    fun givenProtocolIsUnchanged_whenMigrating_thenGroupIsEstablished() = runTest {
        val conversation = TestConversation.CONVERSATION.copy(
            type = Conversation.Type.GROUP,
            teamId = TestTeam.TEAM_ID
        )

        val (arrangement, migrator) = Arrangement()
            .withGetConversationListReturning(listOf(conversation))
            .withUpdateProtocolReturns(Arrangement.UPDATE_PROTOCOL_UNCHANGED)
            .withFetchConversationSucceeding()
            .withGetConversationProtocolInfoReturning(Arrangement.MIXED_PROTOCOL_INFO)
            .withEstablishGroupSucceeds()
            .withGetConversationMembersReturning(Arrangement.MEMBERS)
            .withAddMembersSucceeds()
            .arrange()

        migrator.migrateProteusConversations()

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::updateProtocol)
            .with(eq(conversation.id.toApi()), eq(ConvProtocol.MIXED))
            .wasInvoked(once)

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::establishMLSGroup)
            .with(eq(Arrangement.MIXED_PROTOCOL_INFO.groupId), eq(emptyList()))

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::addMemberToMLSGroup)
            .with(eq(Arrangement.MIXED_PROTOCOL_INFO.groupId), eq(Arrangement.MEMBERS))
    }

    @Test
    fun givenAnError_whenMigrating_thenStillConsiderItASuccess() = runTest {
        val conversation = TestConversation.CONVERSATION.copy(
            type = Conversation.Type.GROUP,
            teamId = TestTeam.TEAM_ID
        )

        val (arrangement, migrator) = Arrangement()
            .withGetConversationListReturning(listOf(conversation))
            .withUpdateProtocolReturns(Arrangement.UPDATE_PROTOCOL_UNCHANGED)
            .withFetchConversationSucceeding()
            .withGetConversationProtocolInfoReturning(Arrangement.MIXED_PROTOCOL_INFO)
            .withEstablishGroupFails()
            .arrange()

        val result = migrator.migrateProteusConversations()
        result.shouldSucceed()
    }

    @Test
    fun givenNonTeamConversation_whenMigrating_thenConversationIsIgnored() = runTest {
        val conversation = TestConversation.CONVERSATION.copy(
            type = Conversation.Type.GROUP,
            teamId = null
        )

        val (arrangement, migrator) = Arrangement()
            .withGetConversationListReturning(listOf(conversation))
            .arrange()

        migrator.migrateProteusConversations()

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::updateProtocol)
            .with(eq(conversation.id.toApi()), eq(ConvProtocol.MIXED))
            .wasNotInvoked()
    }

    @Test
    fun givenOneToOneConversation_whenMigrating_thenConversationIsIgnored() = runTest {
        val conversation = TestConversation.CONVERSATION.copy(
            type = Conversation.Type.ONE_ON_ONE
        )

        val (arrangement, migrator) = Arrangement()
            .withGetConversationListReturning(listOf(conversation))
            .arrange()

        migrator.migrateProteusConversations()

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::updateProtocol)
            .with(eq(conversation.id.toApi()), eq(ConvProtocol.MIXED))
            .wasNotInvoked()
    }

    private class Arrangement {

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val mlsConversationRepository = mock(classOf<MLSConversationRepository>())

        @Mock
        val conversationApi = mock(classOf<ConversationApi>())

        @Mock
        val selfTeamIdProvider = mock(classOf<SelfTeamIdProvider>())

        fun withGetConversationListReturning(conversations: List<Conversation>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationList)
                .whenInvoked()
                .thenReturn(Either.Right(flowOf(conversations)))
        }

        fun withGetConversationProtocolInfoReturning(protocolInfo: Conversation.ProtocolInfo) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationProtocolInfo)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(protocolInfo))
        }

        fun withGetConversationMembersReturning(members: List<UserId>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationMembers)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(members))
        }

        fun withFetchConversationSucceeding() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::fetchConversation)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withUpdateProtocolReturns(response: NetworkResponse<UpdateConversationProtocolResponse>) = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::updateProtocol)
                .whenInvokedWith(anything(), anything())
                .thenReturn(response)
        }

        fun withEstablishGroupSucceeds() = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::establishMLSGroup)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withEstablishGroupFails() = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::establishMLSGroup)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Left(NetworkFailure.ServerMiscommunication(MLS_STALE_MESSAGE_ERROR)))
        }

        fun withAddMembersSucceeds() = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::addMemberToMLSGroup)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(Unit))
        }

        fun arrange() = this to MLSMigratorImpl(
            selfTeamIdProvider,
            conversationRepository,
            mlsConversationRepository,
            conversationApi
        )

        init {
            given(selfTeamIdProvider)
                .suspendFunction(selfTeamIdProvider::invoke)
                .whenInvoked()
                .thenReturn(Either.Right(TestTeam.TEAM_ID))
        }

        companion object {
            val MLS_STALE_MESSAGE_ERROR = KaliumException.InvalidRequestError(ErrorResponse(409, "", "mls-stale-message"))
            val MEMBERS = listOf(TestUser.USER_ID)
            val MIXED_PROTOCOL_INFO = Conversation.ProtocolInfo.MLS(
                TestConversation.GROUP_ID,
                Conversation.ProtocolInfo.MLS.GroupState.PENDING_JOIN,
                0UL,
                Instant.parse("2021-03-30T15:36:00.000Z"),
                cipherSuite = Conversation.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            ) // TODO jacob should be mixed
            val UPDATE_PROTOCOL_SUCCESS = NetworkResponse.Success(
                UpdateConversationProtocolResponse.ProtocolUpdated(
                    EventContentDTO.Conversation.ProtocolUpdate(
                        TestConversation.NETWORK_ID,
                        ConversationProtocolDTO(ConvProtocol.MIXED),
                        TestUser.NETWORK_ID
                    )
                ), emptyMap(), 200
            )
            val UPDATE_PROTOCOL_UNCHANGED = NetworkResponse.Success(
                UpdateConversationProtocolResponse.ProtocolUnchanged,
                emptyMap(), 204)
        }
    }
}
