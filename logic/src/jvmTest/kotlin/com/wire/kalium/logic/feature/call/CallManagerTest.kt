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
import com.wire.kalium.logic.feature.call.usecase.EpochInfoUpdater
import com.wire.kalium.logic.feature.call.usecase.GetCallConversationTypeProvider
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class CallManagerTest {
    private lateinit var testDispatcher: TestDispatcher

    @BeforeTest
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun breakDown() {
        Dispatchers.resetMain()
    }


    @Test
    @Suppress("FunctionNaming") // native function has that name
    fun givenCallManager_whenCallingMessageIsReceived_then_wcall_recv_msg_IsCalled() = runTest {
        val (arrangement, callManager) = Arrangement(testDispatcher.testKaliumDispatcher())
            .onCurrentClientIdReturning(CLIENT_ID.right())
            .onParseToFederatedIdReturning(USER_ID, USER_ID.toString())
            .onParseToFederatedIdReturning(CALL_CONV_ID, CALL_CONV_ID.toString())
            .onWcallCreateReturning(BASE_HANDLE)
            .onWcallRecvMsgReturning(0)
            .onObserveConversationMembersReturning(emptyList())
            .onGetCallConversationTypeReturning(ConversationTypeCalling.Conference)
            .withFetchServerTimeReturning()
            .arrange()

        callManager.onCallingMessageReceived(
            content = CALL_CONTENT,
            message = CALL_MESSAGE,
        )

        verify(VerifyMode.exactly(1)) {
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
        }
    }

    inner class Arrangement(private val kaliumTestDispatcher: KaliumDispatcher) {
        internal val calling = mock<Calling>(MockMode.autoUnit)
        internal val callRepository = mock<CallRepository>()
        internal val messageSender = mock<MessageSender>()
        internal val currentClientIdProvider = mock<CurrentClientIdProvider>()
        internal val mediaManagerService = mock<MediaManagerService>(MockMode.autoUnit)
        internal val flowManagerService = mock<FlowManagerService>(MockMode.autoUnit)
        internal val selfConversationIdProvider = mock<SelfConversationIdProvider>()
        internal val userConfigRepository = mock<UserConfigRepository>()
        internal val conversationRepository = mock<ConversationRepository>()
        internal val federatedIdMapper = mock<FederatedIdMapper>()
        internal val qualifiedIdMapper = mock<QualifiedIdMapper>()
        internal val conversationClientsInCallUpdater = mock<ConversationClientsInCallUpdater>()
        internal val epochInfoUpdater = mock<EpochInfoUpdater>()
        internal val videoStateChecker = mock<VideoStateChecker>()
        internal val networkStateObserver = mock<NetworkStateObserver>()
        internal val getCallConversationType = mock<GetCallConversationTypeProvider>()
        internal val createAndPersistRecentlyEndedCallMetadata = mock<CreateAndPersistRecentlyEndedCallMetadataUseCase>()
        internal val selfUserId = UserId(value = "selfUserId", domain = "selfDomain")
        internal val kaliumConfigs = KaliumConfigs()
        internal val callMapper = CallMapperImpl(qualifiedIdMapper)

        fun onCurrentClientIdReturning(result: Either<CoreFailure, ClientId>) = apply {
            everySuspend { currentClientIdProvider() } returns result
        }

        fun onParseToFederatedIdReturning(id: QualifiedID, result: String) = apply {
            everySuspend { federatedIdMapper.parseToFederatedId(id) } returns result
        }

        fun onWcallCreateReturning(handle: Handle) = apply {
            every {
                calling
                    .wcall_create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } calls { callArgs ->
                val readyHandler = callArgs.args[2] as ReadyHandler
                readyHandler.onReady(0, null)
                handle
            }
        }

        fun onWcallRecvMsgReturning(result: Int) = apply {
            every { calling.wcall_recv_msg(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns result
        }

        fun onObserveConversationMembersReturning(result: List<Conversation.Member>) = apply {
            everySuspend { conversationRepository.observeConversationMembers(any()) } returns flowOf(result)
        }

        fun onGetCallConversationTypeReturning(result: ConversationTypeCalling) = apply {
            everySuspend { getCallConversationType(any()) } returns result
        }

        fun withFetchServerTimeReturning() = apply {
            everySuspend { callRepository.fetchServerTime() } returns "2022-03-30T16:36:00.000Z"
        }

        suspend fun arrange() = this to CallManagerImpl(
            calling = calling,
            callRepository = callRepository,
            currentClientIdProvider = currentClientIdProvider,
            selfConversationIdProvider = selfConversationIdProvider,
            conversationRepository = conversationRepository,
            userConfigRepository = userConfigRepository,
            messageSender = messageSender,
            kaliumDispatchers = kaliumTestDispatcher,
            federatedIdMapper = federatedIdMapper,
            qualifiedIdMapper = qualifiedIdMapper,
            videoStateChecker = videoStateChecker,
            callMapper = callMapper,
            getCallConversationType = getCallConversationType,
            conversationClientsInCallUpdater = conversationClientsInCallUpdater,
            epochInfoUpdater = epochInfoUpdater,
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
