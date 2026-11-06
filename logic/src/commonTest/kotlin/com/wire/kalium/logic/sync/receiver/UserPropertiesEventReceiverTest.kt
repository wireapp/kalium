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

package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.persistence.config.UserConfigStorage
import com.wire.kalium.logic.sync.receiver.handler.UserPropertiesEventHandler
import dev.mokkery.answering.throws
import com.wire.kalium.persistence.dao.conversation.folder.ConversationFolderDAO
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class UserPropertiesEventReceiverTest {

    @Test
    fun givenReadReceiptsUpdateEvent_repositoryIsInvoked() = runTest {
        val event = TestEvent.userPropertyReadReceiptMode()
        val (arrangement, eventReceiver) = Arrangement()
            .withUpdateReadReceiptsSuccess()
            .arrange()

        eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.persistReadReceipts(any())
        }
    }

    @Test
    fun givenFoldersUpdateEvent_repositoryIsInvoked() = runTest {
        val event = TestEvent.foldersUpdate()
        val (arrangement, eventReceiver) = Arrangement()
            .withUpdateConversationFolders()
            .arrange()

        eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationFolderRepository.updateConversationFolders(any())
        }
    }

    @Test
    fun givenTypingIndicatorUpdateSucceeds_whenHandling_thenSuccessIsReturned() = runTest {
        val event = Event.UserProperty.TypingIndicatorModeSet(id = "event-id", value = true)
        val (arrangement, eventReceiver) = Arrangement()
            .withUpdateTypingIndicator(Either.Right(Unit))
            .arrange()

        val result = eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        result.shouldSucceed()
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.persistTypingIndicator(event.value)
        }
    }

    @Test
    fun givenTypingIndicatorUpdateFails_whenHandling_thenFailureIsReturned() = runTest {
        val event = Event.UserProperty.TypingIndicatorModeSet(id = "event-id", value = false)
        val (arrangement, eventReceiver) = Arrangement()
            .withUpdateTypingIndicator(Either.Left(StorageFailure.DataNotFound))
            .arrange()

        val result = eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        result.shouldFail()
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.persistTypingIndicator(event.value)
        }
    }

    @Test
    fun givenScreenshotCensoringEnabledEvent_whenHandling_thenValueIsPersisted() = runTest {
        val event = Event.UserProperty.ScreenshotCensoringModeSet(id = "event-id", value = true)
        val (arrangement, eventReceiver) = Arrangement()
            .withUpdateScreenshotCensoringSuccess()
            .arrange()

        val result = eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        result.shouldSucceed()
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.persistScreenshotCensoring(true)
        }
    }

    @Test
    fun givenScreenshotCensoringDisabledEvent_whenHandling_thenValueIsPersisted() = runTest {
        val event = Event.UserProperty.ScreenshotCensoringModeSet(id = "event-id", value = false)
        val (arrangement, eventReceiver) = Arrangement()
            .withUpdateScreenshotCensoringSuccess()
            .arrange()

        val result = eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        result.shouldSucceed()
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.persistScreenshotCensoring(false)
        }
    }

    private class Arrangement: CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {

        val userConfigRepository = mock<UserConfigStorage>()
        val conversationFolderRepository = mock<ConversationFolderDAO>()

        private val userPropertiesEventReceiver: UserPropertiesEventReceiver = UserPropertiesEventReceiverImpl(
            UserPropertiesEventHandler(userConfigRepository, conversationFolderRepository)
        )

        suspend fun withUpdateReadReceiptsSuccess() = apply {
            everySuspend {
                userConfigRepository.persistReadReceipts(any())
            } returns Unit
        }

        suspend fun withUpdateConversationFolders() = apply {
            everySuspend {
                conversationFolderRepository.updateConversationFolders(any())
             } returns Unit
        }

        suspend fun withUpdateTypingIndicator(result: Either<StorageFailure, Unit>) = apply {
            if (result is Either.Right) {
                everySuspend { userConfigRepository.persistTypingIndicator(any()) } returns Unit
            } else {
                everySuspend {
                    userConfigRepository.persistTypingIndicator(any())
                } throws IllegalStateException("required preference write failed")
            }
        }

        suspend fun withUpdateScreenshotCensoringSuccess() = apply {
            everySuspend { userConfigRepository.persistScreenshotCensoring(any()) } returns Unit
        }

        fun arrange(block: suspend Arrangement.() -> Unit = {}) = let {
            runBlocking { block() }
            this to userPropertiesEventReceiver
        }
    }
}
