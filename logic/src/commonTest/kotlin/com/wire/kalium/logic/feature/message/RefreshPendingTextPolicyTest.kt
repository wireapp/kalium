/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.logic.feature.message

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.logic.framework.TestMessage
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RefreshPendingTextPolicyTest {
    @Test
    fun receiptLookupFailureDoesNotOverwriteGroupPolicy() = runTest {
        val messages = mock<MessageRepository>()
        val properties = mock<UserPropertyRepository>()
        val timer = mock<ObserveSelfDeletionTimerSettingsForConversationUseCase>()
        val pending = TestMessage.TEXT_MESSAGE.copy(isSelfMessage = true, status = Message.Status.Pending,
            editStatus = Message.EditStatus.NotEdited, expirationData = null)
        everySuspend { timer(any(), any()) } returns flowOf(SelfDeletionTimer.Disabled)
        everySuspend { properties.getReadReceiptsStatus() } returns false
        everySuspend { messages.getReceiptModeFromGroupConversationByQualifiedID(any()) } returns
            Either.Left(StorageFailure.DataNotFound)

        assertEquals(Either.Left(StorageFailure.DataNotFound), RefreshPendingTextPolicy(messages, properties, timer)(pending))
        verifySuspend(VerifyMode.exactly(0)) { messages.updatePendingTextPolicy(any(), any(), any(), any()) }
    }

    @Test
    fun groupReceiptsEnabledOverridesPersonalDisabled() = runTest {
        checkReceipts(Conversation.ReceiptMode.ENABLED, personal = false, expected = true)
    }

    @Test
    fun groupReceiptsDisabledOverridesPersonalEnabled() = runTest {
        checkReceipts(Conversation.ReceiptMode.DISABLED, personal = true, expected = false)
    }

    @Test
    fun oneToOneUsesPersonalReceipts() = runTest {
        checkReceipts(null, personal = true, expected = true)
        checkReceipts(null, personal = false, expected = false)
    }

    private suspend fun checkReceipts(mode: Conversation.ReceiptMode?, personal: Boolean, expected: Boolean) {
        val messages = mock<MessageRepository>()
        val properties = mock<UserPropertyRepository>()
        val timer = mock<ObserveSelfDeletionTimerSettingsForConversationUseCase>()
        val pending = TestMessage.TEXT_MESSAGE.copy(isSelfMessage = true, status = Message.Status.Pending,
            editStatus = Message.EditStatus.NotEdited, expirationData = null)
        everySuspend { timer(any(), any()) } returns flowOf(SelfDeletionTimer.Disabled)
        everySuspend { properties.getReadReceiptsStatus() } returns personal
        everySuspend { messages.getReceiptModeFromGroupConversationByQualifiedID(any()) } returns Either.Right(mode)
        everySuspend { messages.updatePendingTextPolicy(any(), any(), any(), any()) } returns Either.Right(Unit)

        val result = RefreshPendingTextPolicy(messages, properties, timer)(pending) as Either.Right

        assertEquals(expected, (result.value as Message.Regular).expectsReadConfirmation)
        verifySuspend(VerifyMode.exactly(1)) {
            messages.updatePendingTextPolicy(pending.conversationId, pending.id, null, expected)
        }
    }
}
