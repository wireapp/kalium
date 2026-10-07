/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.logic.feature.conversation.folder

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.conversation.ConversationFolder
import com.wire.kalium.logic.data.conversation.FolderType
import com.wire.kalium.logic.data.conversation.FolderWithConversations
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderRepository
import com.wire.kalium.logic.data.id.ConversationId

/** Batch membership changes preserve every other folder and archived membership. */
public interface ManageConversationFolderUseCase {
    public suspend fun getFolder(folderId: String): Either<CoreFailure, FolderWithConversations>
    public suspend fun save(
        folderId: String,
        name: String,
        added: Set<ConversationId>,
        removed: Set<ConversationId>,
    ): Either<CoreFailure, ConversationFolder>
    public suspend fun delete(folderId: String): Either<CoreFailure, Unit>
}

internal class ManageConversationFolderUseCaseImpl(
    private val repository: ConversationFolderRepository,
) : ManageConversationFolderUseCase {
    override suspend fun getFolder(folderId: String): Either<CoreFailure, FolderWithConversations> = repository.getFolder(folderId)

    override suspend fun save(
        folderId: String,
        name: String,
        added: Set<ConversationId>,
        removed: Set<ConversationId>,
    ): Either<CoreFailure, ConversationFolder> {
        val folder = ConversationFolder(folderId, name.trim(), FolderType.USER)
        return repository.saveFolder(folder, added, removed).map { folder }
    }

    override suspend fun delete(folderId: String): Either<CoreFailure, Unit> = repository.deleteFolderAndSync(folderId)
}
