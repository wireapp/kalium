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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.verify.VerifyMode
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.verifySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class ConversationClientsInCallUpdaterTest {

    private val callManager = mock<CallManager>(mode = MockMode.autoUnit)
    private val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
    private val federatedIdMapper = mock<FederatedIdMapper>(mode = MockMode.autoUnit)

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
            everySuspend {
                conversationRepository.getConversationRecipientsForCalling(eq(conversationId))
            } returns (Either.Left(CoreFailure.MissingClientRegistration))

            conversationClientsInCallUpdater(conversationId)

            verifySuspend(VerifyMode.not) {
                callManager.updateConversationClients(any(), any())
            }
        }

    @Test
    fun givenConversationRepositoryReturnsValidValues_whenGettingConversationRecipients_thenUpdateConversationClients() =
        runTest(TestKaliumDispatcher.main) {
            everySuspend {
                conversationRepository.getConversationRecipientsForCalling(eq(conversationId))
            } returns (Either.Right(recipients))

            everySuspend {
                federatedIdMapper.parseToFederatedId(userId)
            } returns (userIdString)

            conversationClientsInCallUpdater(conversationId)

            verifySuspend(VerifyMode.exactly(1)) {
                callManager.updateConversationClients(any(), any())
            }
        }

    companion object {
        private val conversationId = ConversationId("conversation", "wire.com")
        private val userId = UserId("someone", "wire.com")
        private const val userIdString = "someone@wire.com"
        private val clientId = ClientId("someone")
        val recipients = listOf(Recipient(userId, listOf(clientId)))
    }
}
