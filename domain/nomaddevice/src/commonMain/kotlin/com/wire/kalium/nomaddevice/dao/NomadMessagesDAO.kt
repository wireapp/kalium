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

package com.wire.kalium.nomaddevice.dao

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import kotlinx.datetime.Instant

internal data class NomadMessageStoreResult(
    val storedMessages: Int,
    val batches: Int,
)

internal interface NomadMessagesDAO {
    suspend fun storeMessages(
        selfUserId: UserId,
        messages: List<MessageEntity.Regular>,
        batchSize: Int,
    ): NomadMessageStoreResult
}

@Suppress("LongParameterList")
internal class NomadMessagesDAOImpl internal constructor(
    private val upsertUsers: suspend (List<UserEntity>) -> Unit,
    private val upsertConversations: suspend (List<ConversationEntity>) -> Unit,
    private val insertMessages: suspend (List<MessageEntity>) -> Unit,
) : NomadMessagesDAO {

    internal constructor(
        userDAO: com.wire.kalium.persistence.dao.UserDAO,
        conversationDAO: com.wire.kalium.persistence.dao.conversation.ConversationDAO,
        messageDAO: com.wire.kalium.persistence.dao.message.MessageDAO,
    ) : this(
        upsertUsers = { users ->
            userDAO.insertOrIgnoreUsers(users)
        },
        upsertConversations = { conversations ->
            conversationDAO.insertOrUpdateLastModified(conversations)
        },
        insertMessages = { messages ->
            messageDAO.insertOrIgnoreMessages(
                messages = messages,
                withUnreadEvents = false,
                checkAssetUpdate = false,
            )
        }
    )

    override suspend fun storeMessages(
        selfUserId: UserId,
        messages: List<MessageEntity.Regular>,
        batchSize: Int,
    ): NomadMessageStoreResult {
        if (messages.isEmpty()) return NomadMessageStoreResult(storedMessages = 0, batches = 0)

        val normalizedBatchSize = batchSize.coerceAtLeast(MIN_BATCH_SIZE)
        upsertUsers(buildPlaceholderUsers(messages))
        upsertConversations(buildPlaceholderConversations(selfUserId, messages))

        var batches = 0
        messages.chunked(normalizedBatchSize).forEach { batch ->
            insertMessages(batch)
            batches += 1
        }

        return NomadMessageStoreResult(
            storedMessages = messages.size,
            batches = batches
        )
    }

    private fun buildPlaceholderUsers(messages: List<MessageEntity.Regular>): List<UserEntity> =
        messages
            .asSequence()
            .map { it.senderUserId }
            .distinct()
            .map { senderId ->
                UserEntity(
                    id = senderId,
                    name = null,
                    handle = null,
                    email = null,
                    phone = null,
                    accentId = DEFAULT_ACCENT_ID,
                    team = null,
                    previewAssetId = null,
                    completeAssetId = null,
                    availabilityStatus = UserAvailabilityStatusEntity.NONE,
                    userType = UserTypeEntity.NONE,
                    botService = null,
                    deleted = false,
                    expiresAt = null,
                    defederated = false,
                    supportedProtocols = null,
                    activeOneOnOneConversationId = null
                )
            }
            .toList()

    private fun buildPlaceholderConversations(
        selfUserId: UserId,
        messages: List<MessageEntity.Regular>,
    ): List<ConversationEntity> =
        messages
            .groupBy(MessageEntity::conversationId)
            .map { (conversationId, conversationMessages) ->
                ConversationEntity(
                    id = conversationId,
                    name = null,
                    type = ConversationEntity.Type.GROUP,
                    teamId = null,
                    protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
                    creatorId = selfUserId.value,
                    lastNotificationDate = null,
                    lastModifiedDate = conversationMessages.maxOf(MessageEntity::date),
                    lastReadDate = Instant.DISTANT_PAST,
                    access = emptyList(),
                    accessRole = emptyList(),
                    receiptMode = ConversationEntity.ReceiptMode.DISABLED,
                    messageTimer = null,
                    userMessageTimer = null,
                    hasIncompleteMetadata = false,
                    archived = false,
                    archivedInstant = null,
                    mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
                    proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
                    legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
                    isChannel = false,
                    channelAccess = null,
                    channelAddPermission = null,
                    wireCell = null,
                    historySharingRetentionSeconds = 0
                )
            }

    private companion object {
        const val MIN_BATCH_SIZE = 1
        const val DEFAULT_ACCENT_ID = 0
    }
}
