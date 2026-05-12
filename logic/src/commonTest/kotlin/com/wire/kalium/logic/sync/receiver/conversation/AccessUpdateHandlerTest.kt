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
package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logic.data.conversation.Conversation.Access
import com.wire.kalium.logic.data.conversation.Conversation.AccessRole
import com.wire.kalium.logic.data.conversation.ConversationMapper
import com.wire.kalium.logic.data.id.PersistenceQualifiedId
import com.wire.kalium.logic.data.message.SystemMessageInserter
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class AccessUpdateHandlerTest {

    @Test
    fun givenConversationAccessUpdateEvent_whenHandlingIt_thenShouldCallUpdateDatabase() = runTest {
        // given
        val event = TestEvent.accessUpdate()

        val (arrangement, eventHandler) = Arrangement()
            .withMappingModelToDAOAccess(
                event.access,
                listOf(ConversationEntity.Access.PRIVATE)
            )
            .withMappingModelToDAOAccessRole(
                event.accessRole,
                listOf(ConversationEntity.AccessRole.TEAM_MEMBER, ConversationEntity.AccessRole.SERVICE)
            )
            .arrange()

        // when
        eventHandler.handle(event)

        // then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationDAO.updateAccess(
                matches {
                    it == PersistenceQualifiedId(
                        value = TestConversation.ID.value,
                        domain = TestConversation.ID.domain
                    )
                },
                matches {
                    it.contains(ConversationEntity.Access.PRIVATE)
                },
                matches {
                    it.contains(ConversationEntity.AccessRole.TEAM_MEMBER) &&
                    it.contains(ConversationEntity.AccessRole.SERVICE)
                }
            )
        }
    }

    @Test
    fun givenAppsAccessEnabled_whenHandlingAccessUpdate_thenSystemMessageIsInserted() = runTest {
        // Given
        val event = TestEvent.accessUpdate().copy(
            accessRole = setOf(AccessRole.TEAM_MEMBER, AccessRole.SERVICE)
        )

        val (arrangement, eventHandler) = Arrangement()
            .withMappingModelToDAOAccess(
                event.access,
                listOf(ConversationEntity.Access.PRIVATE)
            )
            .withMappingModelToDAOAccessRole(
                event.accessRole,
                listOf(ConversationEntity.AccessRole.TEAM_MEMBER, ConversationEntity.AccessRole.SERVICE)
            )
            .withExistingConversationAccessRole(
                listOf(ConversationEntity.AccessRole.TEAM_MEMBER)
            )
            .arrange()

        // When
        eventHandler.handle(event)

        // Then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.systemMessageInserter.insertConversationAppsAccessChanged(
                eventId = eq(event.id),
                conversationId = eq(event.conversationId),
                senderUserId = eq(event.qualifiedFrom),
                isAppsAccessEnabled = eq(true)
            )
        }
    }

    @Test
    fun givenAppsAccessDisabled_whenHandlingAccessUpdate_thenSystemMessageIsInserted() = runTest {
        // Given
        val event = TestEvent.accessUpdate().copy(
            accessRole = setOf(AccessRole.TEAM_MEMBER)
        )

        val (arrangement, eventHandler) = Arrangement()
            .withMappingModelToDAOAccess(
                event.access,
                listOf(ConversationEntity.Access.PRIVATE)
            )
            .withMappingModelToDAOAccessRole(
                event.accessRole,
                listOf(ConversationEntity.AccessRole.TEAM_MEMBER)
            )
            .withExistingConversationAccessRole(
                listOf(ConversationEntity.AccessRole.TEAM_MEMBER, ConversationEntity.AccessRole.SERVICE)
            )
            .arrange()

        // When
        eventHandler.handle(event)

        // Then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.systemMessageInserter.insertConversationAppsAccessChanged(
                eventId = eq(event.id),
                conversationId = eq(event.conversationId),
                senderUserId = eq(event.qualifiedFrom),
                isAppsAccessEnabled = eq(false)
            )
        }
    }

    @Test
    fun givenNoChangeInAppsAccess_whenHandlingAccessUpdate_thenNoSystemMessageIsInserted() = runTest {
        // Given
        val event = TestEvent.accessUpdate().copy(
            accessRole = setOf(AccessRole.TEAM_MEMBER, AccessRole.SERVICE)
        )

        val (arrangement, eventHandler) = Arrangement()
            .withMappingModelToDAOAccess(
                event.access,
                listOf(ConversationEntity.Access.PRIVATE)
            )
            .withMappingModelToDAOAccessRole(
                event.accessRole,
                listOf(ConversationEntity.AccessRole.TEAM_MEMBER, ConversationEntity.AccessRole.SERVICE)
            )
            .withExistingConversationAccessRole(
                listOf(ConversationEntity.AccessRole.TEAM_MEMBER, ConversationEntity.AccessRole.SERVICE)
            )
            .arrange()

        // When
        eventHandler.handle(event)

        // Then
        verifySuspend(VerifyMode.not) {
            arrangement.systemMessageInserter.insertConversationAppsAccessChanged(
                eventId = any(),
                conversationId = any(),
                senderUserId = any(),
                isAppsAccessEnabled = any()
            )
        }
    }

    @Test
    fun givenEventIdProvided_whenHandlingAccessUpdate_thenEventIdIsPassedToSystemMessage() = runTest {
        // Given
        val eventId = "custom-event-id-123"
        val event = TestEvent.accessUpdate(eventId = eventId).copy(
            accessRole = setOf(AccessRole.SERVICE)
        )

        val (arrangement, eventHandler) = Arrangement()
            .withMappingModelToDAOAccess(
                event.access,
                listOf(ConversationEntity.Access.PRIVATE)
            )
            .withMappingModelToDAOAccessRole(
                event.accessRole,
                listOf(ConversationEntity.AccessRole.SERVICE)
            )
            .withExistingConversationAccessRole(
                listOf(ConversationEntity.AccessRole.TEAM_MEMBER)
            )
            .arrange()

        // When
        eventHandler.handle(event)

        // Then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.systemMessageInserter.insertConversationAppsAccessChanged(
                eventId = matches { it == eventId },
                conversationId = any(),
                senderUserId = any(),
                isAppsAccessEnabled = any()
            )
        }
    }

    @Test
    fun givenSenderFromEvent_whenHandlingAccessUpdate_thenSenderIsPassedToSystemMessage() = runTest {
        // Given
        val senderId = TestUser.OTHER_USER_ID
        val event = TestEvent.accessUpdate().copy(
            qualifiedFrom = senderId,
            accessRole = setOf(AccessRole.SERVICE)
        )

        val (arrangement, eventHandler) = Arrangement()
            .withMappingModelToDAOAccess(
                event.access,
                listOf(ConversationEntity.Access.PRIVATE)
            )
            .withMappingModelToDAOAccessRole(
                event.accessRole,
                listOf(ConversationEntity.AccessRole.SERVICE)
            )
            .withExistingConversationAccessRole(
                listOf(ConversationEntity.AccessRole.TEAM_MEMBER)
            )
            .arrange()

        // When
        eventHandler.handle(event)

        // Then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.systemMessageInserter.insertConversationAppsAccessChanged(
                eventId = any(),
                conversationId = any(),
                senderUserId = matches { it == senderId },
                isAppsAccessEnabled = any()
            )
        }
    }

    private class Arrangement {

        val conversationDAO = mock<ConversationDAO>(mode = MockMode.autoUnit)
        val conversationMapper = mock<ConversationMapper>()
        val systemMessageInserter = mock<SystemMessageInserter>(mode = MockMode.autoUnit)

        init {
            everySuspend { conversationDAO.updateAccess(any(), any(), any()) } returns Unit
            everySuspend { conversationDAO.getConversationById(any()) } returns null
            everySuspend {
                systemMessageInserter.insertConversationAppsAccessChanged(
                    eventId = any(),
                    conversationId = any(),
                    senderUserId = any(),
                    isAppsAccessEnabled = any()
                )
            } returns Unit
        }

        private val accessUpdateEventHandler: AccessUpdateEventHandler = AccessUpdateEventHandler(
            selfUserId = TestUser.USER_ID,
            conversationDAO = conversationDAO,
            conversationMapper = conversationMapper,
            systemMessageInserter = systemMessageInserter
        )

        fun withMappingModelToDAOAccess(param: Set<Access>, result: List<ConversationEntity.Access>) = apply {
            every {
                conversationMapper.fromModelToDAOAccess(param)
            } returns result
        }

        fun withMappingModelToDAOAccessRole(param: Set<AccessRole>, result: List<ConversationEntity.AccessRole>) = apply {
            every {
                conversationMapper.fromModelToDAOAccessRole(param)
            } returns result
        }

        fun withInsertMessageReturningUnit() = apply {
            everySuspend {
                systemMessageInserter.insertConversationAppsAccessChanged(
                    eventId = any(),
                    conversationId = any(),
                    senderUserId = any(),
                    isAppsAccessEnabled = any()
                )
            } returns Unit
        }

        suspend fun withExistingConversationAccessRole(accessRoles: List<ConversationEntity.AccessRole>?) = apply {
            val conversationEntity = if (accessRoles != null) {
                TestConversation.ENTITY.copy(accessRole = accessRoles)
            } else {
                null
            }
            everySuspend {
                conversationDAO.getConversationById(any())
            } returns conversationEntity
        }

        fun arrange() = this to accessUpdateEventHandler
    }
}
