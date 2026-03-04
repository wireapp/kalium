package com.wire.kalium.cells.data

import com.wire.kalium.cells.domain.model.Conversation
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CellConversationRepositoryTest {

    private companion object {
        private const val CONVERSATION_ID_1 = "conv1"
        private const val CONVERSATION_DOMAIN_1 = "wire.com"
        private const val CONVERSATION_NAME_1 = "Engineering"
        private const val CONVERSATION_ID_2 = "conv2"
        private const val CONVERSATION_DOMAIN_2 = "wire.com"
        private const val CONVERSATION_NAME_2 = "Design"
    }

    @Test
    fun given_GroupConversationsWithWireCell_whenInvoked_thenReturnConversationDetails() = runTest {
        val groupConv1 = createGroupConversationEntity(CONVERSATION_ID_1, CONVERSATION_DOMAIN_1, CONVERSATION_NAME_1, false, null, "cell1")
        val groupConv2 = createGroupConversationEntity(
            CONVERSATION_ID_2,
            CONVERSATION_DOMAIN_2,
            CONVERSATION_NAME_2,
            true,
            ConversationEntity.ChannelAccess.PUBLIC,
            "conversationId"
        )
        val (_, repository) = Arrangement()
            .withConversations(listOf(groupConv1, groupConv2))
            .arrange()

        val result = repository.getCellGroupConversations()

        assertIs<Either.Right<List<Conversation>>>(result)
        val conversations = result.value
        assertEquals(2, conversations.size)
        assertEquals(CONVERSATION_NAME_1, conversations[0].name)
        assertEquals(CONVERSATION_NAME_2, conversations[1].name)
        assertEquals("$CONVERSATION_ID_1@$CONVERSATION_DOMAIN_1", conversations[0].id)
        assertEquals("$CONVERSATION_ID_2@$CONVERSATION_DOMAIN_2", conversations[1].id)
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
        assertIs<Either.Right<List<Conversation>>>(result)
        assertEquals(0, result.value.size)
    }

    @Test
    fun given_PrivateChannelWithWireCell_whenInvoked_thenReturnConversationDetail() = runTest {
        // Given
        val privateChannel = createGroupConversationEntity(
            CONVERSATION_ID_1,
            CONVERSATION_DOMAIN_1,
            CONVERSATION_NAME_1,
            true,
            ConversationEntity.ChannelAccess.PRIVATE,
            "conversationId"
        )
        val (_, repository) = Arrangement()
            .withConversations(listOf(privateChannel))
            .arrange()

        // When
        val result = repository.getCellGroupConversations()

        // Then
        assertIs<Either.Right<List<Conversation>>>(result)
        val conversations = result.value
        assertEquals(1, conversations.size)
        assertEquals(true, conversations[0].isChannel)
        assertEquals(ConversationDetails.Group.Channel.ChannelAccess.PRIVATE, conversations[0].channelAccess)
    }

    @Test
    fun given_RegularGroupConversation_whenInvoked_thenReturnConversationWithoutChannelAccess() = runTest {
        // Given
        val regularGroup =
            createGroupConversationEntity(CONVERSATION_ID_1, CONVERSATION_DOMAIN_1, CONVERSATION_NAME_1, false, null, "cell1")
        val (_, repository) = Arrangement()
            .withConversations(listOf(regularGroup))
            .arrange()

        // When
        val result = repository.getCellGroupConversations()

        // Then
        assertIs<Either.Right<List<Conversation>>>(result)
        val conversations = result.value
        assertEquals(1, conversations.size)
        assertEquals(false, conversations[0].isChannel)
        assertEquals(null, conversations[0].channelAccess)
    }

    private fun createGroupConversationEntity(
        id: String,
        domain: String,
        name: String,
        isChannel: Boolean,
        channelAccess: ConversationEntity.ChannelAccess?,
        wireCell: String?
    ): ConversationEntity = ConversationEntity(
        id = QualifiedIDEntity(id, domain),
        name = name,
        type = ConversationEntity.Type.GROUP,
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
        isChannel = isChannel,
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
        private val conversationDAO = mock(ConversationDAO::class)
        private var conversations: List<ConversationEntity> = emptyList()

        fun withConversations(convs: List<ConversationEntity>) = apply {
            conversations = convs
        }

        suspend fun arrange(): Pair<Arrangement, CellConversationDataSource> {
            coEvery {
                conversationDAO.getCellGroupConversations()
            }.returns(conversations)

            return this to CellConversationDataSource(conversationDAO)
        }
    }
}
