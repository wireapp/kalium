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

package com.wire.kalium.persistence.dao.message

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.wire.kalium.persistence.MessageAssetViewQueries
import com.wire.kalium.persistence.MessageAttachmentsQueries
import com.wire.kalium.persistence.MessagesQueries
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.asset.AssetMessageEntity
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentEntity
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentMapper
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.kaliumLogger

interface MessageExtensions {
    fun getPagerForConversation(
        conversationId: ConversationIDEntity,
        visibilities: Collection<MessageEntity.Visibility>,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<MessageEntity>

    fun getPagerForMessagesSearch(
        searchQuery: String,
        conversationId: ConversationIDEntity,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<MessageEntity>

    fun getPagerForMessageAssetsWithoutImage(
        conversationId: ConversationIDEntity,
        mimeTypes: Set<String>,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<MessageEntity>

    fun getPagerForMessageAssetImage(
        conversationId: ConversationIDEntity,
        mimeTypes: Set<String>,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<AssetMessageEntity>
}

internal class MessageExtensionsImpl internal constructor(
    private val messagesQueries: MessagesQueries,
    private val messageAttachmentsQueries: MessageAttachmentsQueries,
    private val messageAssetViewQueries: MessageAssetViewQueries,
    private val messageMapper: MessageMapper,
    private val readDispatcher: ReadDispatcher,
) : MessageExtensions {

    override fun getPagerForConversation(
        conversationId: ConversationIDEntity,
        visibilities: Collection<MessageEntity.Visibility>,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<MessageEntity> {
        // We could return a Flow directly, but having the PagingSource is the only way to test this
        return KaliumPager(
            Pager(pagingConfig) { getPagingSource(conversationId, visibilities, startingOffset) },
            getPagingSource(conversationId, visibilities, startingOffset),
            readDispatcher,
        )
    }

    override fun getPagerForMessagesSearch(
        searchQuery: String,
        conversationId: ConversationIDEntity,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<MessageEntity> {
        // We could return a Flow directly, but having the PagingSource is the only way to test this
        return KaliumPager(
            Pager(pagingConfig) { getMessagesSearchPagingSource(searchQuery, conversationId, startingOffset) },
            getMessagesSearchPagingSource(searchQuery, conversationId, startingOffset),
            readDispatcher,
        )
    }

    override fun getPagerForMessageAssetsWithoutImage(
        conversationId: ConversationIDEntity,
        mimeTypes: Set<String>,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<MessageEntity> {
        // We could return a Flow directly, but having the PagingSource is the only way to test this
        return KaliumPager(
            Pager(pagingConfig) { getMessageAssetsWithoutImagePagingSource(conversationId, mimeTypes, startingOffset) },
            getMessageAssetsWithoutImagePagingSource(conversationId, mimeTypes, startingOffset),
            readDispatcher,
        )
    }

    override fun getPagerForMessageAssetImage(
        conversationId: ConversationIDEntity,
        mimeTypes: Set<String>,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<AssetMessageEntity> {
        return KaliumPager(
            Pager(pagingConfig) { getMessageImageAssetsPagingSource(conversationId, mimeTypes, startingOffset) },
            getMessageImageAssetsPagingSource(conversationId, mimeTypes, startingOffset),
            readDispatcher,
        )
    }

    private fun getPagingSource(
        conversationId: ConversationIDEntity,
        visibilities: Collection<MessageEntity.Visibility>,
        initialOffset: Long
    ) = AsyncQueryPagingSource(
        countQuery = messagesQueries.countByConversationIdAndVisibility(conversationId, visibilities),
        context = readDispatcher.value,
        initialOffset = initialOffset,
        queryProvider = { limit, offset ->
            kaliumLogger.d("[QueryPagingSource] Loading [MessageEntity] data: offset = $offset limit = $limit")
            messagesQueries.selectByConversationIdAndVisibility(
                conversationId,
                visibilities,
                limit,
                offset,
                messageMapper::toEntityMessageFromView
            )
        },
        postProcessor = ::withMultipartAttachmentsList
    )

    private fun getMessagesSearchPagingSource(
        searchQuery: String,
        conversationId: ConversationIDEntity,
        initialOffset: Long
    ) = AsyncQueryPagingSource(
        countQuery = messagesQueries.countBySearchedMessageAndConversationId(searchQuery, conversationId),
        context = readDispatcher.value,
        initialOffset = initialOffset,
        queryProvider = { limit, offset ->
            messagesQueries.selectConversationMessagesFromSearch(
                searchQuery,
                conversationId,
                limit,
                offset,
                messageMapper::toEntityMessageFromView
            )
        },
        postProcessor = ::withMultipartAttachmentsList
    )

    private fun getMessageAssetsWithoutImagePagingSource(
        conversationId: ConversationIDEntity,
        mimeTypes: Set<String>,
        initialOffset: Long
    ) = AsyncQueryPagingSource(
        countQuery = messageAssetViewQueries.countAssetMessagesByConversationIdAndMimeTypes(
            conversationId,
            listOf(MessageEntity.Visibility.VISIBLE),
            listOf(MessageEntity.ContentType.ASSET),
            mimeTypes
        ),
        context = readDispatcher.value,
        initialOffset = initialOffset,
        queryProvider = { limit, offset ->
            messageAssetViewQueries.getAssetMessagesByConversationIdAndMimeTypes(
                conversationId,
                listOf(MessageEntity.Visibility.VISIBLE),
                listOf(MessageEntity.ContentType.ASSET),
                mimeTypes,
                limit,
                offset,
                messageMapper::toEntityMessageFromView
            )
        }
    )

    @Suppress("ReturnCount")
    private fun withMultipartAttachments(message: MessageEntity): MessageEntity {
        val regularMessage = message as? MessageEntity.Regular ?: return message
        val multipartContent = regularMessage.content as? MessageEntityContent.Multipart ?: return message
        val attachments = messageAttachmentsQueries.getAttachments(
            regularMessage.id,
            regularMessage.conversationId,
            MessageAttachmentMapper::toDao
        ).executeAsList()
        return regularMessage.copy(content = multipartContent.copy(attachments = attachments))
    }

    private fun withMultipartAttachmentsList(messages: List<MessageEntity>): List<MessageEntity> {
        val multipartIds = messages
            .filterIsInstance<MessageEntity.Regular>()
            .filter { it.content is MessageEntityContent.Multipart }
            .map { it.id }

        if (multipartIds.isEmpty()) return messages

        val attachmentsByMessageId: Map<String, List<MessageAttachmentEntity>> =
            messageAttachmentsQueries
                .getAttachmentsForMessages(multipartIds, MessageAttachmentMapper::toDaoWithMessageId)
                .executeAsList()
                .groupBy(keySelector = { it.first }, valueTransform = { it.second })

        return messages.map { message ->
            val regularMessage = message as? MessageEntity.Regular ?: return@map message
            val multipartContent = regularMessage.content as? MessageEntityContent.Multipart ?: return@map message
            regularMessage.copy(
                content = multipartContent.copy(
                    attachments = attachmentsByMessageId[regularMessage.id] ?: emptyList()
                )
            )
        }
    }

    private fun getMessageImageAssetsPagingSource(
        conversationId: ConversationIDEntity,
        mimeTypes: Set<String>,
        initialOffset: Long
    ) = AsyncQueryPagingSource(
        countQuery = messageAssetViewQueries.countImageAssetMessagesByConversationIdAndMimeTypes(
            conversationId,
            listOf(MessageEntity.Visibility.VISIBLE),
            listOf(MessageEntity.ContentType.ASSET),
            mimeTypes
        ),
        context = readDispatcher.value,
        initialOffset = initialOffset,
        queryProvider = { limit, offset ->
            messageAssetViewQueries.getImageAssetMessagesByConversationIdAndMimeTypes(
                conversationId,
                listOf(MessageEntity.Visibility.VISIBLE),
                listOf(MessageEntity.ContentType.ASSET),
                mimeTypes,
                limit,
                offset,
                messageMapper::toEntityAssetMessageFromView
            )
        }
    )
}
