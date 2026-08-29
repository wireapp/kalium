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

package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.FolderWithConversations
import com.wire.kalium.logic.data.conversation.folders.toDao
import com.wire.kalium.persistence.config.UserConfigStorage
import com.wire.kalium.persistence.dao.conversation.folder.ConversationFolderDAO
import com.wire.kalium.util.InternalKaliumApi

/** Persistence operations required while receiving user-property events. */
@InternalKaliumApi
public interface UserPropertiesConfigRepository {
    public suspend fun setReadReceiptsStatus(enabled: Boolean): Either<StorageFailure, Unit>
    public suspend fun setTypingIndicatorStatus(enabled: Boolean): Either<StorageFailure, Unit>
}

/** Local persistence implementation shared by the app and future bounded receivers. */
@InternalKaliumApi
public class UserPropertiesConfigRepositoryImpl public constructor(
    private val userConfigStorage: UserConfigStorage
) : UserPropertiesConfigRepository {
    override suspend fun setReadReceiptsStatus(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.persistReadReceipts(enabled) }

    override suspend fun setTypingIndicatorStatus(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigStorage.persistTypingIndicator(enabled) }
}

/** Conversation-folder operation required while receiving user-property events. */
@InternalKaliumApi
public fun interface UserPropertiesFolderRepository {
    public suspend fun updateConversationFolders(
        folderWithConversations: List<FolderWithConversations>
    ): Either<CoreFailure, Unit>
}

/** Local folder persistence implementation shared by the app and future bounded receivers. */
@InternalKaliumApi
public class UserPropertiesFolderRepositoryImpl public constructor(
    private val conversationFolderDAO: ConversationFolderDAO
) : UserPropertiesFolderRepository {
    override suspend fun updateConversationFolders(
        folderWithConversations: List<FolderWithConversations>
    ): Either<CoreFailure, Unit> = wrapStorageRequest {
        conversationFolderDAO.updateConversationFolders(folderWithConversations.map { it.toDao() })
    }
}
