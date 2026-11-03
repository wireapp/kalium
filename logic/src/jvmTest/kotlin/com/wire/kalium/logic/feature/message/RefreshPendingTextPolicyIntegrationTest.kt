/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.logic.feature.message

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.PersistMessageUseCaseImpl
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.message.MessageDataSource
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.base.authenticated.message.MessageApi
import com.wire.kalium.persistence.withTestUserDatabase
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RefreshPendingTextPolicyIntegrationTest {
    @Test
    fun oneToOneUsesPersonalReceiptsEnabled() = runTest {
        checkPolicy(ConversationEntity.Type.ONE_ON_ONE, personal = true, expected = true)
    }

    @Test
    fun oneToOneUsesPersonalReceiptsDisabled() = runTest {
        checkPolicy(ConversationEntity.Type.ONE_ON_ONE, personal = false, expected = false)
    }

    @Test
    fun groupReceiptModeStillOverridesPersonalSetting() = runTest {
        checkPolicy(ConversationEntity.Type.GROUP, personal = true, expected = false)
    }

    private suspend fun TestScope.checkPolicy(type: ConversationEntity.Type, personal: Boolean, expected: Boolean) {
        withTestUserDatabase(TestUser.ENTITY_ID) { database ->
            database.builder.conversationDAO.insertConversation(TestConversation.ENTITY.copy(type = type))
            database.builder.userDAO.upsertUser(TestUser.ENTITY)
            // The receipt query, nullable result mapping and policy UPDATE all use the real SQLite DAO and repository.
            val messages = MessageDataSource(
                selfUserId = TestUser.USER_ID,
                messageApi = mock<MessageApi>(),
                mlsMessageApi = mock<MLSMessageApi>(),
                messageDAO = database.builder.messageDAO
            )
            val properties = mock<UserPropertyRepository>()
            val timer = mock<ObserveSelfDeletionTimerSettingsForConversationUseCase>()
            everySuspend { properties.getReadReceiptsStatus() } returns personal
            everySuspend { timer(any(), any()) } returns flowOf(SelfDeletionTimer.Disabled)
            val pending = TestMessage.TEXT_MESSAGE.copy(
                isSelfMessage = true,
                senderUserId = TestUser.USER_ID,
                expectsReadConfirmation = !expected
            )
            val persist = PersistMessageUseCaseImpl(messages, TestUser.USER_ID, mock<NotificationEventsManager>())
            assertIs<Either.Right<Unit>>(persist(pending))
            val beforeRefresh = database.builder.messageDAO.getMessageById(pending.id, TestConversation.ENTITY_ID)
            assertEquals(
                if (type == ConversationEntity.Type.ONE_ON_ONE) !expected else false,
                assertIs<MessageEntity.Regular>(beforeRefresh).expectsReadConfirmation
            )

            val result = assertIs<Either.Right<Message.Sendable>>(RefreshPendingTextPolicy(messages, properties, timer)(pending))
            assertEquals(expected, assertIs<Message.Regular>(result.value).expectsReadConfirmation)
            val persisted = database.builder.messageDAO.getMessageById(pending.id, TestConversation.ENTITY_ID)
            assertEquals(expected, assertIs<MessageEntity.Regular>(persisted).expectsReadConfirmation)
        }
    }
}
