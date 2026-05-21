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
package com.wire.kalium.cells.domain.usecase.offline

import com.wire.kalium.cells.domain.CellFileRepository
import kotlinx.coroutines.flow.Flow

public data class OfflineFileInfo(
    val id: String,
    val conversationId: String?,
    val name: String,
    val mimeType: String? = null,
    val owner: String,
    val localPath: String,
    val size: Long?,
    val downloadedAt: Long,
    val modifiedAt: Long? = null,
)

/**
 * Use case for saving an offline file's information to the local database.
 */
public interface SaveOfflineFileUseCase {
    public suspend operator fun invoke(info: OfflineFileInfo)
}

/**
 * Use case for deleting an offline file's information from the local database by its ID.
 */
public interface DeleteOfflineFileUseCase {
    public suspend operator fun invoke(id: String)
}

/**
 * Use case for observing the list of offline files stored in the local database.
 * Returns a flow that emits the current list of [OfflineFileInfo] whenever it changes.
 */
public interface ObserveOfflineFilesUseCase {
    public operator fun invoke(): Flow<List<OfflineFileInfo>>
}

/**
 * Use case for observing offline files for a specific conversation.
 */
public interface ObserveOfflineFilesByConversationUseCase {
    public operator fun invoke(conversationId: String): Flow<List<OfflineFileInfo>>
}

/**
 * Use case for retrieving a specific offline file's information from the local database by its ID.
 * Returns the [OfflineFileInfo] if found, or null if no file with the given ID exists.
 */
public interface GetOfflineFileUseCase {
    public suspend operator fun invoke(id: String): OfflineFileInfo?
}

internal class SaveOfflineFileUseCaseImpl(
    private val repository: CellFileRepository,
) : SaveOfflineFileUseCase {
    override suspend fun invoke(info: OfflineFileInfo) {
        repository.upsert(info)
    }
}

internal class DeleteOfflineFileUseCaseImpl(
    private val repository: CellFileRepository,
) : DeleteOfflineFileUseCase {
    override suspend fun invoke(id: String) {
        repository.delete(id)
    }
}

internal class ObserveOfflineFilesUseCaseImpl(
    private val repository: CellFileRepository,
) : ObserveOfflineFilesUseCase {
    override fun invoke(): Flow<List<OfflineFileInfo>> = repository.observeOfflineFiles()
}

internal class ObserveOfflineFilesByConversationUseCaseImpl(
    private val repository: CellFileRepository,
) : ObserveOfflineFilesByConversationUseCase {
    override fun invoke(conversationId: String): Flow<List<OfflineFileInfo>> =
        repository.observeOfflineFilesByConversationId(conversationId)
}

internal class GetOfflineFileUseCaseImpl(
    private val repository: CellFileRepository,
) : GetOfflineFileUseCase {
    override suspend fun invoke(id: String): OfflineFileInfo? = repository.getById(id)
}
