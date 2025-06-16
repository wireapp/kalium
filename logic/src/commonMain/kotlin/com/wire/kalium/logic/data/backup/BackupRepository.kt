/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.backup

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationMapper
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageMapper
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@Mockable
interface BackupRepository {
    suspend fun getUsers(): List<OtherUser>
    suspend fun getConversations(): List<Conversation>
    fun getMessages(): Flow<List<Message.Standalone>>
    suspend fun insertUsers(users: List<OtherUser>): Either<CoreFailure, Unit>
    suspend fun insertConversations(conversations: List<Conversation>): Either<CoreFailure, Unit>
    suspend fun insertMessages(messages: List<Message.Standalone>): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList")
internal class BackupDataSource(
    private val selfUserId: UserId,
    private val userDAO: UserDAO,
    private val messageDAO: MessageDAO,
    private val conversationDAO: ConversationDAO,
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(selfUserId),
    private val messageMapper: MessageMapper = MapperProvider.messageMapper(selfUserId),
) : BackupRepository {

    private companion object {
        const val PAGE_SIZE = 100
    }

    override suspend fun getUsers(): List<OtherUser> =
        userDAO.getAllUsersDetails()
            .map { list ->
                list.map { userMapper.fromUserDetailsEntityToOtherUser(it) }
            }.firstOrNull() ?: emptyList()

    override suspend fun getConversations(): List<Conversation> =
        conversationDAO.getAllConversations()
            .map { it.map(conversationMapper::fromDaoModel) }
            .firstOrNull() ?: emptyList()

    override fun getMessages(): Flow<List<Message.Standalone>> = flow {

        var offset = 0L
        var page: List<Message.Standalone>

        do {
            page = messageDAO.getMessagesPage(
                contentTypes = listOf(
                    MessageEntity.ContentType.TEXT,
                    MessageEntity.ContentType.ASSET,
                    MessageEntity.ContentType.LOCATION,
                ),
                offset = offset,
                pageSize = PAGE_SIZE.toLong(),
            ).map {
                messageMapper.fromEntityToMessage(it)
            }

            emit(page)

            offset += PAGE_SIZE

        } while (page.size == PAGE_SIZE)
    }

    override suspend fun insertUsers(users: List<OtherUser>) = wrapStorageRequest {
        userDAO.insertOrIgnoreUsers(users.map { userMapper.fromOtherToUserEntity(it) })
    }

    override suspend fun insertConversations(conversations: List<Conversation>) = wrapStorageRequest {
        conversationDAO.insertOrIgnoreConversations(conversations.map { conversationMapper.fromMigrationModel(it) })
    }

    override suspend fun insertMessages(messages: List<Message.Standalone>) = wrapStorageRequest {
        messageDAO.insertOrIgnoreMessages(messages.map { messageMapper.fromMessageToEntity(it) })
    }
}
