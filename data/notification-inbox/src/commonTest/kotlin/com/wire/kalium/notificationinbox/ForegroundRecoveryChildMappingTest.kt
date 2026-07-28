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

package com.wire.kalium.notificationinbox

import kotlin.test.Test
import kotlin.test.assertEquals

class ForegroundRecoveryChildMappingTest {
    @Test
    fun givenAppliedMessageNeedsForegroundRecovery_whenMappingImport_thenUpsertAndRecoveryAreBothRequired() {
        val child = PendingImportChild(
            childSequence = 1,
            parentIngestSequence = 1,
            scope = InboxScope("account", "client"),
            parentServerEventId = "event",
            itemIndex = 0,
            idempotencyKey = fallbackChildIdempotencyKey("event", 0),
            conversationId = "conversation@domain",
            senderId = "sender@domain",
            senderClientId = "sender-client",
            protocol = ReceiveProtocol.PROTEUS,
            messageTimestampEpochMillis = 1,
            decryptedProto = byteArrayOf(1),
            decryptedProtoSha256 = "digest",
            cryptoStateApplied = true,
            receiveClassification = ReceiveClassification.APPLICATION_MESSAGE,
            failureClassification = "FOREGROUND_RECOVERY_REQUIRED",
            decryptionState = DecryptionState.DECRYPTED,
            notificationState = NotificationState.SUPPRESSED,
            importState = ForegroundImportState.PENDING,
            retryCount = 0
        )

        assertEquals(
            ForegroundImportAction.UPSERT_APPLICATION_MESSAGE_AND_SCHEDULE_FOREGROUND_RECOVERY,
            child.foregroundImportAction()
        )
    }
}
