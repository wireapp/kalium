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

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderRepository
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
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
            arrangement.userConfigRepository.setReadReceiptsStatus(any())
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

    private class Arrangement: CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {

        val userConfigRepository = mock<UserConfigRepository>()
        val conversationFolderRepository = mock<ConversationFolderRepository>()

        private val userPropertiesEventReceiver: UserPropertiesEventReceiver = UserPropertiesEventReceiverImpl(
            userConfigRepository = userConfigRepository,
            conversationFolderRepository = conversationFolderRepository
        )

        suspend fun withUpdateReadReceiptsSuccess() = apply {
            everySuspend {
                userConfigRepository.setReadReceiptsStatus(any())
            } returns Either.Right(Unit)
        }

        suspend fun withUpdateConversationFolders() = apply {
            everySuspend {
                conversationFolderRepository.updateConversationFolders(any())
             } returns Either.Right(Unit)
        }

        fun arrange(block: suspend Arrangement.() -> Unit = {}) = let {
            runBlocking { block() }
            this to userPropertiesEventReceiver
        }
    }
}
