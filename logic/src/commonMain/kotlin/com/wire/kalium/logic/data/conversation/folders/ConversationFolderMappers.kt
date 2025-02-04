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
package com.wire.kalium.logic.data.conversation.folders

import com.wire.kalium.logic.data.conversation.ConversationFolder
import com.wire.kalium.logic.data.conversation.FolderType
import com.wire.kalium.logic.data.conversation.FolderWithConversations
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.network.api.authenticated.properties.LabelDTO
import com.wire.kalium.network.api.authenticated.properties.LabelTypeDTO
import com.wire.kalium.persistence.dao.conversation.folder.ConversationFolderEntity
import com.wire.kalium.persistence.dao.conversation.folder.ConversationFolderTypeEntity
import com.wire.kalium.persistence.dao.conversation.folder.FolderWithConversationsEntity

fun LabelDTO.toFolder(selfDomain: String) = FolderWithConversations(
    conversationIdList = qualifiedConversations?.map { it.toModel() } ?: conversations.map { QualifiedID(it, selfDomain) },
    id = id,
    name = name,
    type = type.toFolderType()
)

fun FolderWithConversations.toLabel() = LabelDTO(
    id = id,
    name = name,
    qualifiedConversations = conversationIdList.map { it.toApi() },
    conversations = conversationIdList.map { it.value },
    type = type.toLabel()
)

fun LabelTypeDTO.toFolderType() = when (this) {
    LabelTypeDTO.USER -> FolderType.USER
    LabelTypeDTO.FAVORITE -> FolderType.FAVORITE
}

fun FolderType.toLabel() = when (this) {
    FolderType.USER -> LabelTypeDTO.USER
    FolderType.FAVORITE -> LabelTypeDTO.FAVORITE
}

fun ConversationFolderEntity.toModel() = ConversationFolder(
    id = id,
    name = name,
    type = type.toModel()
)

fun FolderWithConversationsEntity.toModel() = FolderWithConversations(
    id = id,
    name = name,
    type = type.toModel(),
    conversationIdList = conversationIdList.map { it.toModel() }
)

fun FolderWithConversations.toDao() = FolderWithConversationsEntity(
    id = id,
    name = name,
    type = type.toDao(),
    conversationIdList = conversationIdList.map { it.toDao() }
)

fun FolderType.toDao() = when (this) {
    FolderType.USER -> ConversationFolderTypeEntity.USER
    FolderType.FAVORITE -> ConversationFolderTypeEntity.FAVORITE
}

fun ConversationFolderTypeEntity.toModel(): FolderType = when (this) {
    ConversationFolderTypeEntity.USER -> FolderType.USER
    ConversationFolderTypeEntity.FAVORITE -> FolderType.FAVORITE
}

fun ConversationFolder.toDao() = ConversationFolderEntity(
    id = id,
    name = name,
    type = type.toDao()
)
