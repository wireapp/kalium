/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.client.ClientTypeEntity
import com.wire.kalium.persistence.dao.client.DeviceTypeEntity
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import com.wire.kalium.persistence.dao.client.Client as ClientEntity

@OptIn(ExperimentalCoroutinesApi::class)
class EphemeralRepositoryTest {

    @Test
    fun givenSuccess_whenGettingDeleteMessageRecipients_thenSuccessIsPropagated() = runTest {
        val user = QualifiedIDEntity("userId", "domain.com")
        val conversationId = QualifiedIDEntity("conversationId", "domain.com")
        val clients = listOf(
            ClientEntity(
                user,
                "clientId",
                DeviceTypeEntity.Desktop,
                ClientTypeEntity.Permanent,
                true,
                true,
                null,
                null,
                null
            )
        )

        val expected = Recipient(
            user.toModel(),
            clients.map { ClientId(it.id) }
        )

        val (arrangement, repo) = Arrangement()
            .withDeleteMessageRecipientSuccess(mapOf(user to clients))
            .arrange()

        repo.recipientsForDeletedEphemeral(user.toModel(), conversationId.toModel()).shouldSucceed {
            assertEquals(listOf(expected), it)
        }

        verify(arrangement.clientDAO)
            .suspendFunction(arrangement.clientDAO::recipientsIfTHeyArePartOfConversation)
            .with(eq(conversationId), eq(setOf(user)))
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val clientDAO: ClientDAO = mock(ClientDAO::class)
        private val repo = EphemeralMessageDataSource(clientDAO)

        fun withDeleteMessageRecipientSuccess(result: Map<QualifiedIDEntity, List<ClientEntity>>) = apply {
            given(clientDAO)
                .suspendFunction(clientDAO::recipientsIfTHeyArePartOfConversation)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun arrange() = this to repo
    }
}
