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

package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails.Group.Channel.ChannelAddPermission
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationEventRepositoryImplTest {

    @Test
    fun givenReceiptMode_whenUpdated_thenMappedValueIsWrittenToDao() = runTest {
        val (dao, repository) = arrangement()

        val result = repository.updateReceiptMode(conversationId, Conversation.ReceiptMode.ENABLED)

        assertEquals(Either.Right(Unit), result)
        verifySuspend(VerifyMode.exactly(1)) {
            dao.updateConversationReceiptMode(
                eq(conversationIdEntity),
                eq(ConversationEntity.ReceiptMode.ENABLED),
            )
        }
    }

    @Test
    fun givenMessageTimer_whenUpdated_thenValueIsWrittenToDao() = runTest {
        val (dao, repository) = arrangement()

        val result = repository.updateMessageTimer(conversationId, 42L)

        assertEquals(Either.Right(Unit), result)
        verifySuspend(VerifyMode.exactly(1)) {
            dao.updateMessageTimer(eq(conversationIdEntity), eq(42L))
        }
    }

    @Test
    fun givenAppsAccessBecomesEnabled_whenAccessIsUpdated_thenDaoIsUpdatedBeforeCallbackCompletes() = runTest {
        val (dao, repository) = arrangement()
        everySuspend { dao.getConversationById(eq(conversationIdEntity)) } returns null
        var appsAccessEnabled: Boolean? = null

        val result = repository.updateAccess(
            conversationId = conversationId,
            access = setOf(Conversation.Access.INVITE),
            accessRoles = setOf(Conversation.AccessRole.TEAM_MEMBER, Conversation.AccessRole.SERVICE),
        ) { isEnabled ->
            appsAccessEnabled = isEnabled
        }

        assertEquals(Either.Right(Unit), result)
        assertEquals(true, appsAccessEnabled)
        verifySuspend(VerifyMode.exactly(1)) {
            dao.updateAccess(
                eq(conversationIdEntity),
                eq(listOf(ConversationEntity.Access.INVITE)),
                eq(listOf(ConversationEntity.AccessRole.TEAM_MEMBER, ConversationEntity.AccessRole.SERVICE)),
            )
        }
    }

    @Test
    fun givenChannelPermission_whenUpdated_thenMappedValueIsWrittenToDao() = runTest {
        val (dao, repository) = arrangement()

        val result = repository.updateChannelAddPermissionLocally(conversationId, ChannelAddPermission.EVERYONE)

        assertEquals(Either.Right(Unit), result)
        verifySuspend(VerifyMode.exactly(1)) {
            dao.updateChannelAddPermission(
                eq(conversationIdEntity),
                eq(ConversationEntity.ChannelAddPermission.EVERYONE),
            )
        }
    }

    @Test
    fun givenGuestLinkMutations_whenCalled_thenTheyAreForwardedToDao() = runTest {
        val (dao, repository) = arrangement()

        val updateResult = repository.updateGuestRoomLink(conversationId, "https://wire.example/join", true)
        val deleteResult = repository.deleteGuestRoomLink(conversationId)

        assertEquals(Either.Right(Unit), updateResult)
        assertEquals(Either.Right(Unit), deleteResult)
        verifySuspend(VerifyMode.exactly(1)) {
            dao.updateGuestRoomLink(eq(conversationIdEntity), eq("https://wire.example/join"), eq(true))
        }
        verifySuspend(VerifyMode.exactly(1)) {
            dao.deleteGuestRoomLink(eq(conversationIdEntity))
        }
    }

    @Test
    fun givenDaoFailure_whenUpdatingTimer_thenStorageFailureIsReturned() = runTest {
        val expectedException = IllegalStateException("storage failure")
        val (dao, repository) = arrangement()
        everySuspend { dao.updateMessageTimer(eq(conversationIdEntity), eq(null)) } throws expectedException

        val result = repository.updateMessageTimer(conversationId, null)

        assertEquals(Either.Left(StorageFailure.Generic(expectedException)), result)
    }

    private fun arrangement(): Pair<ConversationDAO, ConversationEventRepository> {
        val dao = mock<ConversationDAO>(mode = MockMode.autoUnit)
        return dao to ConversationEventRepositoryImpl(dao)
    }

    private companion object {
        val conversationId = ConversationId("conversation-id", "wire.example")
        val conversationIdEntity = ConversationIDEntity("conversation-id", "wire.example")
    }
}
