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

package com.wire.kalium.logic.feature.call

import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.VideoStateChecker
import com.wire.kalium.logic.data.call.mapper.CallMapperImpl
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdater
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import kotlinx.datetime.Instant

class CallManagerTest {

    @Mock
    private val calling = mock(Calling::class)

    @Mock
    private val callRepository = mock(CallRepository::class)

    @Mock
    private val userRepository = mock(UserRepository::class)

    @Mock
    private val messageSender = mock(MessageSender::class)

    @Mock
    private val currentClientIdProvider = mock(CurrentClientIdProvider::class)

    @Mock
    private val mediaManagerService = mock(MediaManagerService::class)

    @Mock
    private val flowManagerService = mock(FlowManagerService::class)

    @Mock
    private val selfConversationIdProvider = mock(SelfConversationIdProvider::class)

    @Mock
    private val conversationRepository = mock(ConversationRepository::class)

    @Mock
    private val federatedIdMapper = mock(FederatedIdMapper::class)

    @Mock
    private val qualifiedIdMapper = mock(QualifiedIdMapper::class)

    @Mock
    private val conversationClientsInCallUpdater = mock(ConversationClientsInCallUpdater::class)

    @Mock
    private val videoStateChecker = mock(VideoStateChecker::class)

    @Mock
    private val networkStateObserver = mock(NetworkStateObserver::class)

    private val dispatcher = TestKaliumDispatcher

    private lateinit var callManagerImpl: CallManagerImpl

    private val callMapper = CallMapperImpl(qualifiedIdMapper)

    val kaliumConfigs = KaliumConfigs()

    @BeforeTest
    fun setUp() {
        callManagerImpl = CallManagerImpl(
            calling = calling,
            callRepository = callRepository,
            userRepository = userRepository,
            currentClientIdProvider = currentClientIdProvider,
            selfConversationIdProvider = selfConversationIdProvider,
            conversationRepository = conversationRepository,
            messageSender = messageSender,
            kaliumDispatchers = dispatcher,
            federatedIdMapper = federatedIdMapper,
            qualifiedIdMapper = qualifiedIdMapper,
            videoStateChecker = videoStateChecker,
            callMapper = callMapper,
            conversationClientsInCallUpdater = conversationClientsInCallUpdater,
            networkStateObserver = networkStateObserver,
            kaliumConfigs = kaliumConfigs,
            mediaManagerService = mediaManagerService,
            flowManagerService = flowManagerService,
        )
    }

    @Test
    @Suppress("FunctionNaming") // native function has that name
    @Ignore // This test never really worked. To be fixed in a next PR
    fun givenCallManager_whenCallingMessageIsReceived_then_wcall_recv_msg_IsCalled() = runTest(dispatcher.main) {
        val baseHandle = Handle(value = 0)
        val expectedConversationId = "conversationId"

        callManagerImpl.onCallingMessageReceived(
            content = CALL_CONTENT,
            message = CALL_MESSAGE,
        )

        verify {
            calling.wcall_recv_msg(
                eq(baseHandle),
                eq(CALL_CONTENT.value.toByteArray()),
                eq(CALL_CONTENT.value.toByteArray().size),
                any(),
                any(),
                eq(expectedConversationId),
                eq(USER_ID.toString()),
                eq(CLIENT_ID.value),
                any()
            )
        }.wasInvoked(exactly = once)
    }

    private companion object {
        val CLIENT_ID = ClientId(value = "clientId")
        val USER_ID = UserId(value = "userId", domain = "domainId")
        val CALL_CONTENT = MessageContent.Calling(value = "content")
        val CALL_MESSAGE = Message.Signaling(
            id = "id",
            content = CALL_CONTENT,
            conversationId = ConversationId(value = "value", domain = "domain"),
            date = Instant.parse("2022-03-30T15:36:00.000Z"),
            senderUserId = UserId(value = "value", domain = "domain"),
            senderClientId = ClientId(value = "value"),
            status = Message.Status.Sent,
            isSelfMessage = false,
            expirationData = null
        )
    }
}
