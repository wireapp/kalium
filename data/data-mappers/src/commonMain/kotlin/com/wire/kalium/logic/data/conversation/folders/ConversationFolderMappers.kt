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
import com.wire.kalium.util.InternalKaliumApi

@InternalKaliumApi
public fun LabelDTO.toFolder(selfDomain: String): FolderWithConversations = FolderWithConversations(
    conversationIdList = qualifiedConversations?.map { it.toModel() } ?: conversations.map { QualifiedID(it, selfDomain) },
    id = id,
    name = name,
    type = type.toFolderType()
)

@InternalKaliumApi
public fun FolderWithConversations.toLabel(): LabelDTO = LabelDTO(
    id = id,
    name = name,
    qualifiedConversations = conversationIdList.map { it.toApi() },
    conversations = conversationIdList.map { it.value },
    type = type.toLabel()
)

@InternalKaliumApi
public fun LabelTypeDTO.toFolderType(): FolderType = when (this) {
    LabelTypeDTO.USER -> FolderType.USER
    LabelTypeDTO.FAVORITE -> FolderType.FAVORITE
}

@InternalKaliumApi
public fun FolderType.toLabel(): LabelTypeDTO = when (this) {
    FolderType.USER -> LabelTypeDTO.USER
    FolderType.FAVORITE -> LabelTypeDTO.FAVORITE
}

@InternalKaliumApi
public fun ConversationFolderEntity.toModel(): ConversationFolder = ConversationFolder(
    id = id,
    name = name,
    type = type.toModel()
)

@InternalKaliumApi
public fun FolderWithConversationsEntity.toModel(): FolderWithConversations = FolderWithConversations(
    id = id,
    name = name,
    type = type.toModel(),
    conversationIdList = conversationIdList.map { it.toModel() }
)

@InternalKaliumApi
public fun FolderWithConversations.toDao(): FolderWithConversationsEntity = FolderWithConversationsEntity(
    id = id,
    name = name,
    type = type.toDao(),
    conversationIdList = conversationIdList.map { it.toDao() }
)

@InternalKaliumApi
public fun FolderType.toDao(): ConversationFolderTypeEntity = when (this) {
    FolderType.USER -> ConversationFolderTypeEntity.USER
    FolderType.FAVORITE -> ConversationFolderTypeEntity.FAVORITE
}

@InternalKaliumApi
public fun ConversationFolderTypeEntity.toModel(): FolderType = when (this) {
    ConversationFolderTypeEntity.USER -> FolderType.USER
    ConversationFolderTypeEntity.FAVORITE -> FolderType.FAVORITE
}

@InternalKaliumApi
public fun ConversationFolder.toDao(): ConversationFolderEntity = ConversationFolderEntity(
    id = id,
    name = name,
    type = type.toDao()
)
