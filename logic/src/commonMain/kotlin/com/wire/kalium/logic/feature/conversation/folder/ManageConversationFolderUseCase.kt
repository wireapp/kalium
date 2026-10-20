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
    // These existing public signatures are retained for consumer compatibility.
    @Suppress("konsist.kaliumLogicModuleShouldNotExposeEitherTypesInPublicAPI")
    public suspend fun getFolder(folderId: String): Either<CoreFailure, FolderWithConversations>

    /** Changes membership of an existing folder without changing its name or type, including Favorites. */
    @Suppress("konsist.kaliumLogicModuleShouldNotExposeEitherTypesInPublicAPI")
    public suspend fun updateMembership(
        folderId: String,
        added: Set<ConversationId>,
        removed: Set<ConversationId>,
    ): Either<CoreFailure, ConversationFolder>

    @Suppress("konsist.kaliumLogicModuleShouldNotExposeEitherTypesInPublicAPI")
    public suspend fun save(
        folderId: String,
        name: String,
        added: Set<ConversationId>,
        removed: Set<ConversationId>,
    ): Either<CoreFailure, ConversationFolder>

    @Suppress("konsist.kaliumLogicModuleShouldNotExposeEitherTypesInPublicAPI")
    public suspend fun delete(folderId: String): Either<CoreFailure, Unit>

    /** Saves a new order for the user's folders and syncs it, so other devices show the same order. */
    @Suppress("konsist.kaliumLogicModuleShouldNotExposeEitherTypesInPublicAPI")
    public suspend fun reorder(folderIds: List<String>): Either<CoreFailure, Unit>
}

internal class ManageConversationFolderUseCaseImpl(
    private val repository: ConversationFolderRepository,
) : ManageConversationFolderUseCase {
    override suspend fun getFolder(folderId: String): Either<CoreFailure, FolderWithConversations> = repository.getFolder(folderId)

    override suspend fun updateMembership(
        folderId: String,
        added: Set<ConversationId>,
        removed: Set<ConversationId>,
    ): Either<CoreFailure, ConversationFolder> = repository.updateMembership(folderId, added, removed)

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

    override suspend fun reorder(folderIds: List<String>): Either<CoreFailure, Unit> = repository.reorderFolders(folderIds)
}
