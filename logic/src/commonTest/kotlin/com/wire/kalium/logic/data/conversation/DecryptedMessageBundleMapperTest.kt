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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.CryptoQualifiedID
import com.wire.kalium.cryptography.DecryptedMessageBundle as CryptoDecryptedMessageBundle
import com.wire.kalium.logic.data.id.GroupID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DecryptedMessageBundleMapperTest {

    @Test
    fun givenTextBundle_whenMapping_thenPreservesRequiredTextFields() {
        val result = assertIs<DecryptedMessageBundle.Text>(
            CryptoDecryptedMessageBundle.Text(MESSAGE, SENDER_CLIENT_ID, identity = null).toModel(GROUP_ID)
        )

        assertEquals(GROUP_ID, result.groupID)
        assertContentEquals(MESSAGE, result.applicationMessage.message)
        assertEquals(SENDER_CLIENT_ID.value, result.applicationMessage.senderClientID.value)
        assertEquals(SENDER_CLIENT_ID.userId.value, result.applicationMessage.senderID.value)
        assertEquals(SENDER_CLIENT_ID.userId.domain, result.applicationMessage.senderID.domain)
    }

    @Test
    fun givenCommitBundle_whenMapping_thenPreservesCommitState() {
        val result = assertIs<DecryptedMessageBundle.Commit>(
            CryptoDecryptedMessageBundle.Commit(isActive = false, identity = null).toModel(GROUP_ID)
        )

        assertEquals(GROUP_ID, result.groupID)
        assertEquals(false, result.isActive)
    }

    @Test
    fun givenProposalBundle_whenMapping_thenPreservesCommitDelay() {
        val result = assertIs<DecryptedMessageBundle.Proposal>(
            CryptoDecryptedMessageBundle.Proposal(commitDelay = COMMIT_DELAY, identity = null).toModel(GROUP_ID)
        )

        assertEquals(GROUP_ID, result.groupID)
        assertEquals(COMMIT_DELAY, result.commitDelay)
    }

    private companion object {
        val GROUP_ID = GroupID("group-id")
        val MESSAGE = "message".encodeToByteArray()
        val SENDER_CLIENT_ID = CryptoQualifiedClientId(
            value = "client-id",
            userId = CryptoQualifiedID("user-id", "domain.example")
        )
        const val COMMIT_DELAY = 10L
    }
}
