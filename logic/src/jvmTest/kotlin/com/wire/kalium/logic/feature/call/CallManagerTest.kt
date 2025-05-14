/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.call

import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.ConversationTypeCalling
import com.wire.kalium.calling.callbacks.ReadyHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.VideoStateChecker
import com.wire.kalium.logic.data.call.mapper.CallMapperImpl
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdater
import com.wire.kalium.logic.feature.call.usecase.CreateAndPersistRecentlyEndedCallMetadataUseCase
import com.wire.kalium.logic.feature.call.usecase.GetCallConversationTypeProvider
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.network.NetworkStateObserver
import io.mockative.any
import io.mockative.coEvery
import io.mockative.eq
import io.mockative.every
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

class CallManagerTest {
    private val dispatcher = TestKaliumDispatcher

    @Test
    @Suppress("FunctionNaming") // native function has that name
    fun givenCallManager_whenCallingMessageIsReceived_then_wcall_recv_msg_IsCalled() = runTest(dispatcher.main) {
        val (arrangement, callManager) = Arrangement()
            .onCurrentClientIdReturning(CLIENT_ID.right())
            .onParseToFederatedIdReturning(USER_ID, USER_ID.toString())
            .onParseToFederatedIdReturning(CALL_CONV_ID, CALL_CONV_ID.toString())
            .onWcallCreateReturning(BASE_HANDLE)
            .onWcallRecvMsgReturning(0)
            .onObserveConversationMembersReturning(emptyList())
            .onGetCallConversationTypeReturning(ConversationTypeCalling.Conference)
            .arrange()

        callManager.onCallingMessageReceived(
            content = CALL_CONTENT,
            message = CALL_MESSAGE,
        )

        verify {
            arrangement.calling.wcall_recv_msg(
                eq(BASE_HANDLE),
                matches { it.contentEquals(CALL_CONTENT.value.toByteArray()) },
                eq(CALL_CONTENT.value.toByteArray().size),
                any(),
                any(),
                eq(CALL_CONV_ID.toString()),
                eq(USER_ID.toString()),
                eq(CLIENT_ID.value),
                any()
            )
        }.wasInvoked(exactly = once)
    }

    inner class Arrangement {
        internal val calling = mock(Calling::class)
        internal val callRepository = mock(CallRepository::class)
        internal val messageSender = mock(MessageSender::class)
        internal val currentClientIdProvider = mock(CurrentClientIdProvider::class)
        internal val mediaManagerService = mock(MediaManagerService::class)
        internal val flowManagerService = mock(FlowManagerService::class)
        internal val selfConversationIdProvider = mock(SelfConversationIdProvider::class)
        internal val userConfigRepository = mock(UserConfigRepository::class)
        internal val conversationRepository = mock(ConversationRepository::class)
        internal val federatedIdMapper = mock(FederatedIdMapper::class)
        internal val qualifiedIdMapper = mock(QualifiedIdMapper::class)
        internal val conversationClientsInCallUpdater = mock(ConversationClientsInCallUpdater::class)
        internal val videoStateChecker = mock(VideoStateChecker::class)
        internal val networkStateObserver = mock(NetworkStateObserver::class)
        internal val getCallConversationType = mock(GetCallConversationTypeProvider::class)
        internal val createAndPersistRecentlyEndedCallMetadata = mock(CreateAndPersistRecentlyEndedCallMetadataUseCase::class)
        internal val selfUserId = UserId(value = "selfUserId", domain = "selfDomain")
        internal val kaliumConfigs = KaliumConfigs()
        internal val callMapper = CallMapperImpl(qualifiedIdMapper)

        suspend fun onCurrentClientIdReturning(result: Either<CoreFailure, ClientId>) = apply {
            coEvery { currentClientIdProvider() }.returns(result)
        }

        suspend fun onParseToFederatedIdReturning(id: QualifiedID, result: String) = apply {
            coEvery { federatedIdMapper.parseToFederatedId(id) }.returns(result)
        }

        fun onWcallCreateReturning(handle: Handle) = apply {
            every {
                calling
                    .wcall_create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            }.invokes {
                val readyHandler = it[2] as ReadyHandler
                readyHandler.onReady(0, null)
                handle
            }
        }

        fun onWcallRecvMsgReturning(result: Int) = apply {
            every { calling.wcall_recv_msg(any(), any(), any(), any(), any(), any(), any(), any(), any()) }.returns(result)
        }

        suspend fun onObserveConversationMembersReturning(result: List<Conversation.Member>) = apply {
            coEvery { conversationRepository.observeConversationMembers(any()) }.returns(flowOf(result))
        }

        suspend fun onGetCallConversationTypeReturning(result: ConversationTypeCalling) = apply {
            coEvery { getCallConversationType(any()) }.returns(result)
        }

        suspend fun arrange() = this to CallManagerImpl(
            calling = calling,
            callRepository = callRepository,
            currentClientIdProvider = currentClientIdProvider,
            selfConversationIdProvider = selfConversationIdProvider,
            conversationRepository = conversationRepository,
            userConfigRepository = userConfigRepository,
            messageSender = messageSender,
            kaliumDispatchers = dispatcher,
            federatedIdMapper = federatedIdMapper,
            qualifiedIdMapper = qualifiedIdMapper,
            videoStateChecker = videoStateChecker,
            callMapper = callMapper,
            getCallConversationType = getCallConversationType,
            conversationClientsInCallUpdater = conversationClientsInCallUpdater,
            networkStateObserver = networkStateObserver,
            kaliumConfigs = kaliumConfigs,
            mediaManagerService = mediaManagerService,
            flowManagerService = flowManagerService,
            createAndPersistRecentlyEndedCallMetadata = createAndPersistRecentlyEndedCallMetadata,
            selfUserId = selfUserId
        ).also {
            onParseToFederatedIdReturning(selfUserId, selfUserId.toString())
        }
    }

    private companion object {
        val BASE_HANDLE = Handle(value = 0)
        val CLIENT_ID = ClientId(value = "clientId")
        val USER_ID = UserId(value = "userId", domain = "domainId")
        val CALL_CONV_ID = ConversationId(value = "value", domain = "domain")
        val CALL_CONTENT = MessageContent.Calling(value = "{\"type\": \"TYPE\"}", conversationId = CALL_CONV_ID)
        val CALL_MESSAGE = Message.Signaling(
            id = "id",
            content = CALL_CONTENT,
            conversationId = CALL_CONV_ID,
            date = Instant.parse("2022-03-30T15:36:00.000Z"),
            senderUserId = USER_ID,
            senderClientId = CLIENT_ID,
            status = Message.Status.Sent,
            isSelfMessage = false,
            expirationData = null
        )
    }
}
