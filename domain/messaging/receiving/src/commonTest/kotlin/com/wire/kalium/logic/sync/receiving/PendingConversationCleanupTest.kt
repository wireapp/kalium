/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.pendingaction.PendingActionType
import com.wire.kalium.persistence.withTestUserDatabase
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test
import kotlin.test.assertEquals

class PendingConversationCleanupTest {
    @Test
    fun legacyReceiveRecoveryDeletionMovesToTheWipeQueueAndEveryRecordIsDropped() = runTest {
        withTestUserDatabase(UserIDEntity("cleanup-test", "example.com")) { database ->
            val db = database.builder
            val prefix = "mls_receive_recovery_v2:${"client".encodeUtf8().sha256().hex()}:"
            val receipt = """{"eventId":"delete-event","payloadHash":"${"a".repeat(64)}"}"""
            db.metadataDAO.insertValue(receipt.dropLast(1) + ""","groupId":"deleted-group"}""",
                prefix + "deletion:" + receipt.encodeUtf8().sha256().hex())
            db.metadataDAO.insertValue(receipt, prefix + "payload:" + "b".repeat(64))
            db.metadataDAO.insertValue("""{"eventId":"e","groupId":"g","ciphertextHash":"${"c".repeat(64)}"}""", prefix + "d".repeat(64))
            db.metadataDAO.insertValue("kept", "unrelated")
            val cleanup = PendingConversationCleanup(db) {}

            repeat(2) { cleanup.discardLegacyReceiveRecovery() }

            assertEquals(listOf("deleted-group"),
                db.pendingActionDAO.getByActionType(PendingActionType.WIPE_MLS_GROUP).map { it.qualifiedId.value })
            assertEquals(emptyMap(), db.metadataDAO.valuesByKeyRange("mls_receive_recovery_v2:", "mls_receive_recovery_v2;"))
            assertEquals("kept", db.metadataDAO.valueByKey("unrelated"))
        }
    }
}
