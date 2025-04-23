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
package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class ConversationClientsInCallUpdaterTest {

    private val callManager = mock(CallManager::class)
    private val conversationRepository = mock(ConversationRepository::class)
    private val federatedIdMapper = mock(FederatedIdMapper::class)

    private lateinit var conversationClientsInCallUpdater: ConversationClientsInCallUpdater

    @BeforeTest
    fun setup() {
        conversationClientsInCallUpdater = ConversationClientsInCallUpdaterImpl(
            callManager = lazy { callManager },
            conversationRepository = conversationRepository,
            federatedIdMapper = federatedIdMapper,
            dispatchers = TestKaliumDispatcher
        )
    }

    @Test
    fun givenConversationRepositoryReturnsFailure_whenGettingConversationRecipients_thenDoNothing() =
        runTest(TestKaliumDispatcher.main) {
            coEvery {
                conversationRepository.getConversationRecipientsForCalling(eq(conversationId))
            }.returns(Either.Left(CoreFailure.MissingClientRegistration))

            conversationClientsInCallUpdater(conversationId)

            coVerify {
                callManager.updateConversationClients(any(), any())
            }.wasNotInvoked()
        }

    @Test
    fun givenConversationRepositoryReturnsValidValues_whenGettingConversationRecipients_thenUpdateConversationClients() =
        runTest(TestKaliumDispatcher.main) {
            coEvery {
                conversationRepository.getConversationRecipientsForCalling(eq(conversationId))
            }.returns(Either.Right(recipients))

            coEvery {
                federatedIdMapper.parseToFederatedId(userId)
            }.returns(userIdString)

            conversationClientsInCallUpdater(conversationId)

            coVerify {
                callManager.updateConversationClients(any(), any())
            }.wasInvoked(once)
        }

    companion object {
        private val conversationId = ConversationId("conversation", "wire.com")
        private val userId = UserId("someone", "wire.com")
        private const val userIdString = "someone@wire.com"
        private val clientId = ClientId("someone")
        val recipients = listOf(Recipient(userId, listOf(clientId)))
    }
}
