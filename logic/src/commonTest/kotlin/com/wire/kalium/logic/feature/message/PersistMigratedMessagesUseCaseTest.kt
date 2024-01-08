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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.message.MigratedMessage
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.dao.MigrationDAO
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.messages.GenericMessage
import com.wire.kalium.protobuf.messages.Text
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PersistMigratedMessagesUseCaseTest {

    @Test
    fun givenAValidProtoMessage_whenMigratingMessage_thenShouldReturnASuccessResult() = runTest {
        // Given
        val (arrangement, persistMigratedMessages) = Arrangement()
            .withMessagesInsertedSuccessfully()
            .arrange()

        // When
        val result = persistMigratedMessages(listOf(arrangement.fakeMigratedMessage()), TestScope())

        // Then
        assertTrue(result is Either.Right)
    }

    private class Arrangement {
        val selfUserId: UserId = SELF_USER_ID

        @Mock
        val migrationDAO: MigrationDAO = mock(MigrationDAO::class)

        val genericMessage = GenericMessage("uuid", GenericMessage.Content.Text(Text("some_text")))

        fun fakeMigratedMessage() = MigratedMessage(
            conversationId = TestConversation.ID,
            senderUserId = TestUser.USER_ID,
            senderClientId = TestClient.CLIENT_ID,
            0,
            "some_content",
            null,
            genericMessage.encodeToByteArray(),
            null,
            null,
            null,
        )

        fun withMessagesInsertedSuccessfully(): Arrangement {
            given(migrationDAO)
                .suspendFunction(migrationDAO::insertMessages)
                .whenInvokedWith(any())
                .thenReturn(Unit)
            return this
        }

        fun arrange() = this to PersistMigratedMessagesUseCaseImpl(selfUserId, migrationDAO)
    }

    companion object {
        val SELF_USER_ID = UserId("user-id", "domain")
    }

}
