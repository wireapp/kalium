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

package com.wire.kalium.persistence.dao.message

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.MessageThreadsQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import com.wire.kalium.persistence.util.mapToList
import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

data class MessageThreadRootEntity(
    val conversationId: QualifiedIDEntity,
    val rootMessageId: String,
    val threadId: String,
    val createdAt: Instant,
    val visibleReplyCount: Long,
    val lastReplyDate: Instant?,
)

data class MessageThreadSummaryEntity(
    val conversationId: QualifiedIDEntity,
    val rootMessageId: String,
    val threadId: String,
    val visibleReplyCount: Long,
)

private data class MessageThreadItemStateEntity(
    val threadId: String,
    val isRoot: Boolean,
    val visibility: MessageEntity.Visibility,
    val creationDate: Instant,
)

@Mockable
interface MessageThreadDAO {
    suspend fun upsertThreadRoot(
        conversationId: QualifiedIDEntity,
        rootMessageId: String,
        threadId: String,
        createdAt: Instant,
    )

    suspend fun upsertThreadItem(
        conversationId: QualifiedIDEntity,
        messageId: String,
        threadId: String,
        isRoot: Boolean,
        creationDate: Instant,
        visibility: MessageEntity.Visibility,
    )

    suspend fun getThreadByRootMessage(
        conversationId: QualifiedIDEntity,
        rootMessageId: String,
    ): MessageThreadRootEntity?

    suspend fun getRootMessageIdByThreadId(
        conversationId: QualifiedIDEntity,
        threadId: String,
    ): String?

    suspend fun getThreadIdByMessageId(
        conversationId: QualifiedIDEntity,
        messageId: String,
    ): String?

    fun observeThreadSummariesForRoots(
        conversationId: QualifiedIDEntity,
        rootMessageIds: List<String>,
    ): Flow<List<MessageThreadSummaryEntity>>
}

internal class MessageThreadDAOImpl internal constructor(
    private val queries: MessageThreadsQueries,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher,
) : MessageThreadDAO {

    override suspend fun upsertThreadRoot(
        conversationId: QualifiedIDEntity,
        rootMessageId: String,
        threadId: String,
        createdAt: Instant,
    ) {
        withContext(writeDispatcher.value) {
            queries.upsertThreadRoot(
                conversation_id = conversationId,
                root_message_id = rootMessageId,
                thread_id = threadId,
                created_at = createdAt,
            )
            queries.refreshThreadVisibleReplyCount(
                conversation_id = conversationId,
                thread_id = threadId,
            )
        }
    }

    override suspend fun upsertThreadItem(
        conversationId: QualifiedIDEntity,
        messageId: String,
        threadId: String,
        isRoot: Boolean,
        creationDate: Instant,
        visibility: MessageEntity.Visibility,
    ) {
        withContext(writeDispatcher.value) {
            queries.transaction {
                val previous = queries.getThreadItemByMessageId(
                    conversation_id = conversationId,
                    message_id = messageId,
                    mapper = ::MessageThreadItemStateEntity
                ).executeAsOneOrNull()

                queries.upsertThreadItem(
                    conversation_id = conversationId,
                    thread_id = threadId,
                    message_id = messageId,
                    creation_date = creationDate,
                    is_root = isRoot,
                    visibility = visibility,
                )

                if (isRoot) {
                    queries.upsertMainListItem(
                        conversation_id = conversationId,
                        message_id = messageId,
                        creation_date = creationDate,
                        visibility = visibility,
                    )
                } else {
                    queries.deleteMainListItem(
                        conversation_id = conversationId,
                        message_id = messageId,
                    )
                }

                updateThreadCountersAfterUpsert(
                    conversationId = conversationId,
                    previous = previous,
                    threadId = threadId,
                    isRoot = isRoot,
                    creationDate = creationDate,
                    visibility = visibility,
                )
            }
        }
    }

    private fun updateThreadCountersAfterUpsert(
        conversationId: QualifiedIDEntity,
        previous: MessageThreadItemStateEntity?,
        threadId: String,
        isRoot: Boolean,
        creationDate: Instant,
        visibility: MessageEntity.Visibility,
    ) {
        if (isRoot) {
            if (previous?.isRoot == false && previous.visibility == MessageEntity.Visibility.VISIBLE) {
                queries.decrementThreadVisibleReplyCount(
                    conversation_id = conversationId,
                    thread_id = previous.threadId,
                )
            }
            return
        }

        if (previous == null) {
            if (visibility == MessageEntity.Visibility.VISIBLE) {
                queries.incrementThreadVisibleReplyCount(
                    conversation_id = conversationId,
                    thread_id = threadId,
                    reply_date = creationDate,
                )
            }
            return
        }

        if (previous.isRoot) {
            if (visibility == MessageEntity.Visibility.VISIBLE) {
                queries.incrementThreadVisibleReplyCount(
                    conversation_id = conversationId,
                    thread_id = threadId,
                    reply_date = creationDate,
                )
            }
            return
        }

        if (previous.threadId != threadId) {
            if (previous.visibility == MessageEntity.Visibility.VISIBLE) {
                queries.decrementThreadVisibleReplyCount(
                    conversation_id = conversationId,
                    thread_id = previous.threadId,
                )
            }
            if (visibility == MessageEntity.Visibility.VISIBLE) {
                queries.incrementThreadVisibleReplyCount(
                    conversation_id = conversationId,
                    thread_id = threadId,
                    reply_date = creationDate,
                )
            }
            return
        }

        if (previous.visibility == visibility) return

        when {
            previous.visibility == MessageEntity.Visibility.VISIBLE -> {
                queries.decrementThreadVisibleReplyCount(
                    conversation_id = conversationId,
                    thread_id = threadId,
                )
            }

            visibility == MessageEntity.Visibility.VISIBLE -> {
                queries.incrementThreadVisibleReplyCount(
                    conversation_id = conversationId,
                    thread_id = threadId,
                    reply_date = creationDate,
                )
            }
        }
    }

    override suspend fun getThreadByRootMessage(
        conversationId: QualifiedIDEntity,
        rootMessageId: String,
    ): MessageThreadRootEntity? = withContext(readDispatcher.value) {
        queries.getThreadByRootMessage(
            conversation_id = conversationId,
            root_message_id = rootMessageId,
            mapper = ::MessageThreadRootEntity
        ).executeAsOneOrNull()
    }

    override suspend fun getRootMessageIdByThreadId(conversationId: QualifiedIDEntity, threadId: String): String? =
        withContext(readDispatcher.value) {
            queries.getRootMessageIdByThreadId(
                conversation_id = conversationId,
                thread_id = threadId
            ).executeAsOneOrNull()
        }

    override suspend fun getThreadIdByMessageId(
        conversationId: QualifiedIDEntity,
        messageId: String,
    ): String? = withContext(readDispatcher.value) {
        queries.getThreadIdByMessageId(
            conversation_id = conversationId,
            message_id = messageId
        ).executeAsOneOrNull()
    }

    override fun observeThreadSummariesForRoots(
        conversationId: QualifiedIDEntity,
        rootMessageIds: List<String>,
    ): Flow<List<MessageThreadSummaryEntity>> {
        if (rootMessageIds.isEmpty()) return flowOf(emptyList())
        return queries.selectThreadSummariesByRootIds(
            conversation_id = conversationId,
            root_message_ids = rootMessageIds,
            mapper = ::MessageThreadSummaryEntity
        )
            .asFlow()
            .mapToList()
            .flowOn(readDispatcher.value)
    }
}
