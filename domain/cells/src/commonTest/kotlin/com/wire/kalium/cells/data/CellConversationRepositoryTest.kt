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
package com.wire.kalium.cells.data

import com.wire.kalium.cells.domain.model.CellConversation
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CellConversationRepositoryTest {

    private companion object {
        private val CONVERSATION_ID_1 = ConversationId("conv1", "wire.com")
        private const val CONVERSATION_NAME_1 = "Engineering"
        private val CONVERSATION_ID_2 = ConversationId("conv2", "wire.com")
        private const val CONVERSATION_NAME_2 = "Design"
    }

    @Test
    fun given_GroupConversationsWithWireCell_whenInvoked_thenReturnConversationDetails() = runTest {
        val groupConv1 = createGroupConversationEntity(
            CONVERSATION_ID_1,
            CONVERSATION_NAME_1,
            ConversationEntity.Type.GROUP,
            null,
            "cell1"
        )
        val groupConv2 = createGroupConversationEntity(
            CONVERSATION_ID_2,
            CONVERSATION_NAME_2,
            ConversationEntity.Type.CHANNEL,
            ConversationEntity.ChannelAccess.PUBLIC,
            "conversationId"
        )
        val (_, repository) = Arrangement()
            .withConversations(listOf(groupConv1, groupConv2))
            .arrange()

        val result = repository.getCellGroupConversations()

        assertIs<Either.Right<List<CellConversation>>>(result)
        val conversations = result.value
        assertEquals(2, conversations.size)
        assertEquals(CONVERSATION_NAME_1, conversations[0].name)
        assertEquals(CONVERSATION_NAME_2, conversations[1].name)
        assertEquals(CONVERSATION_ID_1, conversations[0].id)
        assertEquals(CONVERSATION_ID_2, conversations[1].id)
        assertEquals(false, conversations[0].isChannel)
        assertEquals(true, conversations[1].isChannel)
        assertEquals(null, conversations[0].channelAccess)
        assertEquals(ConversationDetails.Group.Channel.ChannelAccess.PUBLIC, conversations[1].channelAccess)
    }

    @Test
    fun given_EmptyConversations_whenInvoked_thenReturnEmptyList() = runTest {
        // Given
        val (_, repository) = Arrangement()
            .withConversations(emptyList())
            .arrange()

        // When
        val result = repository.getCellGroupConversations()

        // Then
        assertIs<Either.Right<List<CellConversation>>>(result)
        assertEquals(0, result.value.size)
    }

    @Test
    fun given_PrivateChannelWithWireCell_whenInvoked_thenReturnConversationDetail() = runTest {
        // Given
        val privateChannel = createGroupConversationEntity(
            CONVERSATION_ID_1,
            CONVERSATION_NAME_1,
            ConversationEntity.Type.CHANNEL,
            ConversationEntity.ChannelAccess.PRIVATE,
            "conversationId"
        )
        val (_, repository) = Arrangement()
            .withConversations(listOf(privateChannel))
            .arrange()

        // When
        val result = repository.getCellGroupConversations()

        // Then
        assertIs<Either.Right<List<CellConversation>>>(result)
        val conversations = result.value
        assertEquals(1, conversations.size)
        assertEquals(true, conversations[0].isChannel)
        assertEquals(ConversationDetails.Group.Channel.ChannelAccess.PRIVATE, conversations[0].channelAccess)
    }

    // ── getPaginatedCellGroupConversations ──────────────────────────────────────

    @Test
    fun given_GroupConversationsWithWireCell_whenGetPaginatedCalled_thenReturnsMappedConversations() = runTest {
        val groupConv1 = createGroupConversationEntity(
            CONVERSATION_ID_1,
            CONVERSATION_NAME_1,
            ConversationEntity.Type.GROUP,
            null,
            "cell1"
        )
        val groupConv2 = createGroupConversationEntity(
            CONVERSATION_ID_2,
            CONVERSATION_NAME_2,
            ConversationEntity.Type.CHANNEL,
            ConversationEntity.ChannelAccess.PUBLIC,
            "conversationId"
        )
        val (_, repository) = Arrangement()
            .withPagedConversations(listOf(groupConv1, groupConv2))
            .arrange()

        val result = repository.getPaginatedCellGroupConversations(limit = 20, offset = 0)

        assertIs<Either.Right<List<CellConversation>>>(result)
        val conversations = result.value
        assertEquals(2, conversations.size)
        assertEquals(CONVERSATION_NAME_1, conversations[0].name)
        assertEquals(CONVERSATION_NAME_2, conversations[1].name)
        assertEquals(CONVERSATION_ID_1, conversations[0].id)
        assertEquals(CONVERSATION_ID_2, conversations[1].id)
        assertEquals(false, conversations[0].isChannel)
        assertEquals(true, conversations[1].isChannel)
        assertEquals(null, conversations[0].channelAccess)
        assertEquals(ConversationDetails.Group.Channel.ChannelAccess.PUBLIC, conversations[1].channelAccess)
    }

    @Test
    fun given_EmptyResult_whenGetPaginatedCalled_thenReturnsEmptyList() = runTest {
        val (_, repository) = Arrangement()
            .withPagedConversations(emptyList())
            .arrange()

        val result = repository.getPaginatedCellGroupConversations(limit = 20, offset = 0)

        assertIs<Either.Right<List<CellConversation>>>(result)
        assertEquals(0, result.value.size)
    }

    @Test
    fun given_PrivateChannel_whenGetPaginatedCalled_thenMapsPrivateChannelAccess() = runTest {
        val privateChannel = createGroupConversationEntity(
            CONVERSATION_ID_1,
            CONVERSATION_NAME_1,
            type = ConversationEntity.Type.CHANNEL,
            channelAccess = ConversationEntity.ChannelAccess.PRIVATE,
            wireCell = "cell1"
        )
        val (_, repository) = Arrangement()
            .withPagedConversations(listOf(privateChannel))
            .arrange()

        val result = repository.getPaginatedCellGroupConversations(limit = 20, offset = 0)

        assertIs<Either.Right<List<CellConversation>>>(result)
        val conversations = result.value
        assertEquals(1, conversations.size)
        assertEquals(true, conversations[0].isChannel)
        assertEquals(ConversationDetails.Group.Channel.ChannelAccess.PRIVATE, conversations[0].channelAccess)
    }

    @Test
    fun given_PublicChannel_whenGetPaginatedCalled_thenMapsPublicChannelAccess() = runTest {
        val publicChannel = createGroupConversationEntity(
            CONVERSATION_ID_1,
            CONVERSATION_NAME_1,
            type = ConversationEntity.Type.CHANNEL,
            channelAccess = ConversationEntity.ChannelAccess.PUBLIC,
            wireCell = "cell1"
        )
        val (_, repository) = Arrangement()
            .withPagedConversations(listOf(publicChannel))
            .arrange()

        val result = repository.getPaginatedCellGroupConversations(limit = 20, offset = 0)

        assertIs<Either.Right<List<CellConversation>>>(result)
        val conversations = result.value
        assertEquals(1, conversations.size)
        assertEquals(true, conversations[0].isChannel)
        assertEquals(ConversationDetails.Group.Channel.ChannelAccess.PUBLIC, conversations[0].channelAccess)
    }

    @Test
    fun given_RegularGroupConversation_whenGetPaginatedCalled_thenNoChannelAccess() = runTest {
        val regularGroup = createGroupConversationEntity(
            CONVERSATION_ID_1,
            CONVERSATION_NAME_1,
            channelAccess = null,
            wireCell = "cell1"
        )
        val (_, repository) = Arrangement()
            .withPagedConversations(listOf(regularGroup))
            .arrange()

        val result = repository.getPaginatedCellGroupConversations(limit = 20, offset = 0)

        assertIs<Either.Right<List<CellConversation>>>(result)
        val conversations = result.value
        assertEquals(1, conversations.size)
        assertEquals(false, conversations[0].isChannel)
        assertEquals(null, conversations[0].channelAccess)
    }

    @Test
    fun given_PaginationOffset_whenGetPaginatedCalled_thenPassesOffsetToDao() = runTest {
        val groupConv = createGroupConversationEntity(
            CONVERSATION_ID_2,
            CONVERSATION_NAME_2,
            ConversationEntity.Type.GROUP,
            null,
            "cell2"
        )
        val (_, repository) = Arrangement()
            .withPagedConversations(offset = 20, conversations = listOf(groupConv))
            .arrange()

        val result = repository.getPaginatedCellGroupConversations(limit = 20, offset = 20)

        assertIs<Either.Right<List<CellConversation>>>(result)
        assertEquals(1, result.value.size)
        assertEquals(CONVERSATION_NAME_2, result.value[0].name)
    }

    // ── getCellGroupConversations (existing) ────────────────────────────────────

    @Test
    fun given_ConversationsWithNullAndEmptyNames_whenInvoked_thenFilterThemOut() = runTest {
        // Given
        val validConv = createGroupConversationEntity(
            CONVERSATION_ID_1,
            CONVERSATION_NAME_1,
            ConversationEntity.Type.GROUP,
            null,
            "cell1"
        )
        val nullNameConv = createGroupConversationEntity(
            CONVERSATION_ID_2,
            "",
            ConversationEntity.Type.GROUP,
            null,
            "cell2"
        )
        val (_, repository) = Arrangement()
            .withConversations(listOf(validConv, nullNameConv))
            .arrange()

        // When
        val result = repository.getCellGroupConversations()

        // Then
        assertIs<Either.Right<List<CellConversation>>>(result)
        val conversations = result.value
        assertEquals(1, conversations.size)
        assertEquals(CONVERSATION_NAME_1, conversations[0].name)
        assertEquals(CONVERSATION_ID_1, conversations[0].id)
    }

    @Test
    fun given_ConversationWithTeam_whenGetConversationTeamId_thenReturnTeamId() = runTest {
        val conv = createGroupConversationEntity(
            CONVERSATION_ID_1,
            CONVERSATION_NAME_1,
            channelAccess = null,
            wireCell = "cell1"
        )
        val (_, repository) = Arrangement()
            .withConversationById(conv)
            .arrange()

        val result = repository.getConversationTeamId("conv1@wire.com")

        assertIs<Either.Right<String?>>(result)
        assertEquals("team123", result.value)
    }

    private fun createGroupConversationEntity(
        conversationId: ConversationId,
        name: String,
        type: ConversationEntity.Type = ConversationEntity.Type.GROUP,
        channelAccess: ConversationEntity.ChannelAccess?,
        wireCell: String?
    ): ConversationEntity = ConversationEntity(
        id = QualifiedIDEntity(conversationId.value, conversationId.domain),
        name = name,
        type = type,
        teamId = "team123",
        mutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED,
        creatorId = "creator@wire.com",
        lastModifiedDate = Instant.fromEpochMilliseconds(0L),
        lastNotificationDate = null,
        lastReadDate = Instant.fromEpochMilliseconds(0L),
        archived = false,
        archivedInstant = null,
        legalHoldStatus = ConversationEntity.LegalHoldStatus.ENABLED,
        access = emptyList(),
        accessRole = emptyList(),
        channelAccess = channelAccess,
        wireCell = wireCell,
        protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
        receiptMode = ConversationEntity.ReceiptMode.ENABLED,
        messageTimer = null,
        userMessageTimer = null,
        mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        channelAddPermission = null,
        historySharingRetentionSeconds = 0L
    )

    private class Arrangement {
        private val conversationDAO = mock<ConversationDAO>(mode = MockMode.autoUnit)
        private var conversations: List<ConversationEntity> = emptyList()
        private var pagedConversations: Map<Int, List<ConversationEntity>> = emptyMap()
        private var conversationById: ConversationEntity? = null

        fun withConversations(convs: List<ConversationEntity>) = apply {
            conversations = convs
        }

        fun withConversationById(conv: ConversationEntity?) = apply {
            conversationById = conv
        }

        fun withPagedConversations(conversations: List<ConversationEntity>, offset: Int = 0) = apply {
            pagedConversations = pagedConversations + (offset to conversations)
        }

        suspend fun arrange(): Pair<Arrangement, CellConversationDataSource> {
            everySuspend {
                conversationDAO.getCellGroupConversations()
            }.returns(conversations)

            if (pagedConversations.isNotEmpty()) {
                pagedConversations.forEach { (offset, convs) ->
                    everySuspend {
                        conversationDAO.getCellGroupConversationsPaged(20, offset, "")
                    }.returns(convs)
                }
            } else {
                everySuspend {
                    conversationDAO.getCellGroupConversationsPaged(any(), any(), any())
                }.returns(emptyList())
            }

            everySuspend {
                conversationDAO.getConversationById(any())
            }.returns(conversationById)

            return this to CellConversationDataSource(conversationDAO)
        }
    }
}
