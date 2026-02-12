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

package com.wire.kalium.logic.data.message

import app.cash.turbine.test
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.id.ConversationId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuntimeTransportStatusTrackerTest {

    @Test
    fun whenTrackingSendingMessages_thenStateIsUpdatedAndFlowEmits() = runTest {
        val tracker = RuntimeTransportStatusTrackerImpl()
        val conversationId = ConversationId("conversation-id", "example.com")
        val messageId = "message-id"

        tracker.updates.test {
            awaitItem()
            tracker.markMessageSending(conversationId, messageId)
            awaitItem()
            assertTrue(tracker.isMessageSending(conversationId, messageId))

            tracker.clearMessageSending(conversationId, messageId)
            awaitItem()
            assertFalse(tracker.isMessageSending(conversationId, messageId))
        }
    }

    @Test
    fun whenTrackingAssetTransfer_thenInProgressStateIsUpdatedAndFlowEmits() = runTest {
        val tracker = RuntimeTransportStatusTrackerImpl()
        val conversationId = ConversationId("conversation-id", "example.com")
        val messageId = "message-id"

        tracker.updates.test {
            awaitItem()
            tracker.markAssetInProgress(conversationId, messageId, AssetTransferStatus.UPLOAD_IN_PROGRESS)
            awaitItem()
            assertEquals(
                AssetTransferStatus.UPLOAD_IN_PROGRESS,
                tracker.getAssetInProgressStatus(conversationId, messageId)
            )

            tracker.clearAssetInProgress(conversationId, messageId)
            awaitItem()
            assertNull(tracker.getAssetInProgressStatus(conversationId, messageId))
        }
    }

    @Test
    fun givenNonInProgressAssetStatus_whenTracking_thenStatusIsNotStored() = runTest {
        val tracker = RuntimeTransportStatusTrackerImpl()
        val conversationId = ConversationId("conversation-id", "example.com")
        val messageId = "message-id"

        tracker.markAssetInProgress(conversationId, messageId, AssetTransferStatus.FAILED_UPLOAD)

        assertNull(tracker.getAssetInProgressStatus(conversationId, messageId))
    }
}
