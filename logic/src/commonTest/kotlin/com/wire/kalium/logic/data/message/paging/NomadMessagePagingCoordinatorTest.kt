/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.message.paging

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.network.NetworkState
import com.wire.kalium.network.api.authenticated.nomaddevice.Conversation
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadBatchRestoreRequest
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadBatchRestoreResponse
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadConversationBatchRestore
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadStoredMessage
import com.wire.kalium.network.api.base.authenticated.nomaddevice.NomadDeviceSyncApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.backup.NomadMessageStoreResult
import com.wire.kalium.persistence.dao.backup.NomadMessagesDAO
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NomadMessagePagingCoordinatorTest {

    @Test
    fun givenNomadDisabled_whenFetching_thenNoNetworkCall() = runTest {
        val (arrangement, coordinator) = Arrangement()
            .withNomadEnabled(false)
            .arrange()

        coordinator.fetchOlderMessagesIfNeeded(CONVERSATION_ID, pageSize = 5, beforeTimestampMs = 111L) {}

        verifySuspend(VerifyMode.exactly(0)) { arrangement.nomadDeviceSyncApi.restoreMessagesBatch(any()) }
    }

    @Test
    fun givenHasMoreTrue_whenFetchingTwice_thenUsesTwoRequests() = runTest {
        val (arrangement, coordinator) = Arrangement()
            .withRestoreMessagesBatchSuccess(hasMore = true, nextCursor = 10, nextTimestamp = 50)
            .arrange()

        coordinator.fetchOlderMessagesIfNeeded(CONVERSATION_ID, pageSize = 5, beforeTimestampMs = 1000L) {}
        coordinator.fetchOlderMessagesIfNeeded(CONVERSATION_ID, pageSize = 5, beforeTimestampMs = 1000L) {}

        verifySuspend(VerifyMode.exactly(2)) { arrangement.nomadDeviceSyncApi.restoreMessagesBatch(any()) }
    }

    @Test
    fun givenHasMoreFalse_whenFetchingTwice_thenOnlyFirstRequestIsMade() = runTest {
        val (arrangement, coordinator) = Arrangement()
            .withRestoreMessagesBatchSuccess(hasMore = false, nextCursor = 0, nextTimestamp = 50)
            .arrange()

        coordinator.fetchOlderMessagesIfNeeded(CONVERSATION_ID, pageSize = 5, beforeTimestampMs = 1000L) {}
        coordinator.fetchOlderMessagesIfNeeded(CONVERSATION_ID, pageSize = 5, beforeTimestampMs = 1000L) {}

        verifySuspend(VerifyMode.exactly(1)) { arrangement.nomadDeviceSyncApi.restoreMessagesBatch(any()) }
    }

    @Test
    fun givenNetworkError_whenFetching_thenAllowsRetry() = runTest {
        val (arrangement, coordinator) = Arrangement()
            .withRestoreMessagesBatchError()
            .arrange()

        coordinator.fetchOlderMessagesIfNeeded(CONVERSATION_ID, pageSize = 5, beforeTimestampMs = 1000L) {}
        coordinator.fetchOlderMessagesIfNeeded(CONVERSATION_ID, pageSize = 5, beforeTimestampMs = 1000L) {}

        verifySuspend(VerifyMode.exactly(2)) { arrangement.nomadDeviceSyncApi.restoreMessagesBatch(any()) }
    }

    @Test
    fun givenStoreReturnsMessages_whenFetching_thenInvalidates() = runTest {
        val (arrangement, coordinator) = Arrangement()
            .withRestoreMessagesBatchSuccess(hasMore = false, nextCursor = 0, nextTimestamp = 50)
            .withStoreMessagesResult(NomadMessageStoreResult(storedMessages = 2, batches = 1))
            .arrange()

        var invalidated = 0
        coordinator.fetchOlderMessagesIfNeeded(CONVERSATION_ID, pageSize = 5, beforeTimestampMs = 1000L) {
            invalidated += 1
        }

        assertEquals(1, invalidated)
    }

    @Test
    fun givenStoreReturnsZero_whenFetching_thenDoesNotInvalidate() = runTest {
        val (arrangement, coordinator) = Arrangement()
            .withRestoreMessagesBatchSuccess(hasMore = false, nextCursor = 0, nextTimestamp = 50)
            .withStoreMessagesResult(NomadMessageStoreResult(storedMessages = 0, batches = 0))
            .arrange()

        var invalidated = 0
        coordinator.fetchOlderMessagesIfNeeded(CONVERSATION_ID, pageSize = 5, beforeTimestampMs = 1000L) {
            invalidated += 1
        }

        assertEquals(0, invalidated)
    }

    @Test
    fun givenNoBeforeTimestamp_whenFetching_thenUsesClockTimestamp() = runTest {
        val capturedRequests = mutableListOf<Long?>()
        val (arrangement, coordinator) = Arrangement()
            .withRestoreMessagesBatchSuccess(hasMore = false, nextCursor = 0, nextTimestamp = 50)
            .arrange()

        everySuspend { arrangement.nomadDeviceSyncApi.restoreMessagesBatch(any()) } calls { invocation ->
            val request = invocation.args.first() as NomadBatchRestoreRequest
            capturedRequests.add(request.beforeTimestamp)
            arrangement.restoreResponse
        }

        coordinator.fetchOlderMessagesIfNeeded(CONVERSATION_ID, pageSize = 5, beforeTimestampMs = null) {}

        assertEquals(1, capturedRequests.size)
        assertTrue(capturedRequests.first() != null)
    }


    private data class Arrangement(
        val nomadDeviceSyncApi: NomadDeviceSyncApi = mock(),
        val nomadMessagesDAO: NomadMessagesDAO = mock(),
        val nomadEnabled: Boolean = true,
        val restoreResponse: NetworkResponse<NomadBatchRestoreResponse> = NetworkResponse.Success(
            value = NomadBatchRestoreResponse(emptyList()),
            headers = emptyMap(),
            httpCode = 200,
        ),
        val storeResult: NomadMessageStoreResult = NomadMessageStoreResult(storedMessages = 0, batches = 0),
    ) {
        fun withNomadEnabled(enabled: Boolean): Arrangement = copy(nomadEnabled = enabled)

        fun withRestoreMessagesBatchSuccess(hasMore: Boolean, nextCursor: Long, nextTimestamp: Long): Arrangement {
            val response = NetworkResponse.Success(
                value = NomadBatchRestoreResponse(
                    conversations = listOf(
                        NomadConversationBatchRestore(
                            conversation = Conversation(id = CONVERSATION_ID.value, domain = CONVERSATION_ID.domain),
                            messages = listOf(
                                NomadStoredMessage(
                                    messageId = "msg1",
                                    timestamp = 1L,
                                    payload = "",
                                    reaction = "",
                                    readReceipt = ""
                                )
                            ),
                            hasMore = hasMore,
                            nextCursor = nextCursor,
                            nextTimestamp = nextTimestamp,
                        )
                    )
                ),
                headers = emptyMap(),
                httpCode = 200
            )
            return copy(restoreResponse = response)
        }

        fun withStoreMessagesResult(result: NomadMessageStoreResult): Arrangement = copy(storeResult = result)

        fun withRestoreMessagesBatchError(): Arrangement = copy(
            restoreResponse = NetworkResponse.Error(KaliumException.NoNetwork(NetworkState.NotConnected))
        )

        fun arrange(): Pair<Arrangement, NomadMessagePagingCoordinator> {
            everySuspend { nomadDeviceSyncApi.restoreMessagesBatch(any()) } returns restoreResponse
            everySuspend { nomadMessagesDAO.storeMessages(any(), any()) } returns storeResult

            val coordinator = NomadMessagePagingCoordinatorImpl(
                selfUserId = SELF_USER_ID,
                isNomadEnabled = { nomadEnabled },
                nomadDeviceSyncApi = nomadDeviceSyncApi,
                nomadMessagesDAO = nomadMessagesDAO,
                clock = FIXED_CLOCK,
            )
            return this to coordinator
        }
    }

    private companion object {
        val SELF_USER_ID: UserId = TestUser.SELF.id
        val CONVERSATION_ID: QualifiedID = TestConversation.ID
        val FIXED_CLOCK: Clock = Clock.System
    }
}
