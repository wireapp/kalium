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
package com.wire.kalium.cells

import com.wire.kalium.cells.data.CellAttachmentsDataSource
import com.wire.kalium.cells.data.CellConversationDataSource
import com.wire.kalium.cells.data.CellUploadManagerImpl
import com.wire.kalium.cells.data.CellUsersDataSource
import com.wire.kalium.cells.data.CellsApiImpl
import com.wire.kalium.cells.data.CellsAwsClient
import com.wire.kalium.cells.data.CellsDataSource
import com.wire.kalium.cells.data.MessageAttachmentDraftDataSource
import com.wire.kalium.cells.data.cellsAwsClient
import com.wire.kalium.cells.domain.CellAttachmentsRepository
import com.wire.kalium.cells.domain.CellConversationRepository
import com.wire.kalium.cells.domain.CellUploadManager
import com.wire.kalium.cells.domain.CellUsersRepository
import com.wire.kalium.cells.domain.CellsApi
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.NodeServiceBuilder
import com.wire.kalium.cells.domain.model.CellsCredentials
import com.wire.kalium.cells.domain.usecase.AddAttachmentDraftUseCase
import com.wire.kalium.cells.domain.usecase.AddAttachmentDraftUseCaseImpl
import com.wire.kalium.cells.domain.usecase.CreateFolderUseCase
import com.wire.kalium.cells.domain.usecase.CreateFolderUseCaseImpl
import com.wire.kalium.cells.domain.usecase.DeleteCellAssetUseCase
import com.wire.kalium.cells.domain.usecase.DeleteCellAssetUseCaseImpl
import com.wire.kalium.cells.domain.usecase.DeleteMessageAttachmentsUseCase
import com.wire.kalium.cells.domain.usecase.DeleteMessageAttachmentsUseCaseImpl
import com.wire.kalium.cells.domain.usecase.DownloadCellFileUseCase
import com.wire.kalium.cells.domain.usecase.DownloadCellFileUseCaseImpl
import com.wire.kalium.cells.domain.usecase.GetAllTagsUseCase
import com.wire.kalium.cells.domain.usecase.GetAllTagsUseCaseImpl
import com.wire.kalium.cells.domain.usecase.GetCellFileUseCase
import com.wire.kalium.cells.domain.usecase.GetCellFileUseCaseImpl
import com.wire.kalium.cells.domain.usecase.GetCellFilesPagedUseCase
import com.wire.kalium.cells.domain.usecase.GetCellFilesPagedUseCaseImpl
import com.wire.kalium.cells.domain.usecase.GetEditorUrlUseCase
import com.wire.kalium.cells.domain.usecase.GetEditorUrlUseCaseImpl
import com.wire.kalium.cells.domain.usecase.GetFoldersUseCase
import com.wire.kalium.cells.domain.usecase.GetFoldersUseCaseImpl
import com.wire.kalium.cells.domain.usecase.GetMessageAttachmentUseCase
import com.wire.kalium.cells.domain.usecase.GetMessageAttachmentUseCaseImpl
import com.wire.kalium.cells.domain.usecase.GetPaginatedNodesUseCase
import com.wire.kalium.cells.domain.usecase.GetPaginatedNodesUseCaseImpl
import com.wire.kalium.cells.domain.usecase.IsAtLeastOneCellAvailableUseCase
import com.wire.kalium.cells.domain.usecase.IsAtLeastOneCellAvailableUseCaseImpl
import com.wire.kalium.cells.domain.usecase.MoveNodeUseCase
import com.wire.kalium.cells.domain.usecase.MoveNodeUseCaseImpl
import com.wire.kalium.cells.domain.usecase.ObserveAttachmentDraftsUseCase
import com.wire.kalium.cells.domain.usecase.ObserveAttachmentDraftsUseCaseImpl
import com.wire.kalium.cells.domain.usecase.PublishAttachmentsUseCase
import com.wire.kalium.cells.domain.usecase.PublishAttachmentsUseCaseImpl
import com.wire.kalium.cells.domain.usecase.RefreshCellAssetStateUseCase
import com.wire.kalium.cells.domain.usecase.RefreshCellAssetStateUseCaseImpl
import com.wire.kalium.cells.domain.usecase.RemoveAttachmentDraftUseCase
import com.wire.kalium.cells.domain.usecase.RemoveAttachmentDraftUseCaseImpl
import com.wire.kalium.cells.domain.usecase.RemoveAttachmentDraftsUseCase
import com.wire.kalium.cells.domain.usecase.RemoveAttachmentDraftsUseCaseImpl
import com.wire.kalium.cells.domain.usecase.RemoveNodeTagsUseCase
import com.wire.kalium.cells.domain.usecase.RemoveNodeTagsUseCaseImpl
import com.wire.kalium.cells.domain.usecase.RenameNodeUseCase
import com.wire.kalium.cells.domain.usecase.RenameNodeUseCaseImpl
import com.wire.kalium.cells.domain.usecase.RestoreNodeFromRecycleBinUseCase
import com.wire.kalium.cells.domain.usecase.RestoreNodeFromRecycleBinUseCaseImpl
import com.wire.kalium.cells.domain.usecase.RetryAttachmentUploadUseCase
import com.wire.kalium.cells.domain.usecase.RetryAttachmentUploadUseCaseImpl
import com.wire.kalium.cells.domain.usecase.UpdateNodeTagsUseCase
import com.wire.kalium.cells.domain.usecase.UpdateNodeTagsUseCaseImpl
import com.wire.kalium.cells.domain.usecase.publiclink.CreatePublicLinkPasswordUseCase
import com.wire.kalium.cells.domain.usecase.publiclink.CreatePublicLinkPasswordUseCaseImpl
import com.wire.kalium.cells.domain.usecase.publiclink.CreatePublicLinkUseCase
import com.wire.kalium.cells.domain.usecase.publiclink.CreatePublicLinkUseCaseImpl
import com.wire.kalium.cells.domain.usecase.publiclink.DeletePublicLinkUseCase
import com.wire.kalium.cells.domain.usecase.publiclink.DeletePublicLinkUseCaseImpl
import com.wire.kalium.cells.domain.usecase.publiclink.GetPublicLinkPasswordUseCase
import com.wire.kalium.cells.domain.usecase.publiclink.GetPublicLinkPasswordUseCaseImpl
import com.wire.kalium.cells.domain.usecase.publiclink.GetPublicLinkUseCase
import com.wire.kalium.cells.domain.usecase.publiclink.GetPublicLinkUseCaseImpl
import com.wire.kalium.cells.domain.usecase.publiclink.SetPublicLinkExpirationUseCase
import com.wire.kalium.cells.domain.usecase.publiclink.SetPublicLinkExpirationUseCaseImpl
import com.wire.kalium.cells.domain.usecase.publiclink.UpdatePublicLinkPasswordUseCase
import com.wire.kalium.cells.domain.usecase.publiclink.UpdatePublicLinkPasswordUseCaseImpl
import com.wire.kalium.cells.domain.usecase.versioning.GetNodeVersionsUseCase
import com.wire.kalium.cells.domain.usecase.versioning.GetNodeVersionsUseCaseImpl
import com.wire.kalium.cells.sdk.kmp.api.NodeServiceApi
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.asset.AssetDAO
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentsDao
import com.wire.kalium.persistence.dao.messageattachment.MessageAttachmentDraftDao
import com.wire.kalium.persistence.dao.publiclink.PublicLinkDao
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okio.FileSystem
import okio.SYSTEM
import kotlin.coroutines.CoroutineContext

public class CellsScope(
    private val cellsClient: HttpClient,
    private val dao: CellScopeDao,
    private val sessionManager: SessionManager,
    private val accessTokenApi: AccessTokenApi,
) : CoroutineScope {

    public data class CellScopeDao(
        val attachmentDraftDao: MessageAttachmentDraftDao,
        val conversationsDao: ConversationDAO,
        val attachmentsDao: MessageAttachmentsDao,
        val assetsDao: AssetDAO,
        val userDao: UserDAO,
        val publicLinkDao: PublicLinkDao,
    )

    override val coroutineContext: CoroutineContext = SupervisorJob()

    private val cellClientCredentials: CellsCredentials
        get() = CellsCredentialsProvider.getCredentials(sessionManager.serverConfig())

    private val cellAwsClient: CellsAwsClient
        get() = cellsAwsClient(cellClientCredentials, sessionManager, accessTokenApi)

    private val nodeServiceApi: NodeServiceApi
        get() = NodeServiceBuilder
            .withHttpClient(cellsClient)
            .withCredentials(cellClientCredentials)
            .build()

    private val cellsApi: CellsApi
        get() = CellsApiImpl(nodeServiceApi = nodeServiceApi)

    private val cellsRepository: CellsRepository
        get() = CellsDataSource(
            cellsApi = cellsApi,
            publicLinkDao = dao.publicLinkDao,
            awsClient = cellAwsClient,
            fileSystem = FileSystem.SYSTEM
        )

    private val cellsConversationRepository: CellConversationRepository
        get() = CellConversationDataSource(dao.conversationsDao)

    private val cellAttachmentsRepository: CellAttachmentsRepository
        get() = CellAttachmentsDataSource(dao.attachmentsDao, dao.assetsDao)

    public val messageAttachmentsDraftRepository: MessageAttachmentDraftRepository
        get() = MessageAttachmentDraftDataSource(dao.attachmentDraftDao)

    private val usersRepository: CellUsersRepository
        get() = CellUsersDataSource(dao.userDao)

    public val uploadManager: CellUploadManager by lazy {
        CellUploadManagerImpl(
            repository = cellsRepository,
            uploadScope = this,
        )
    }

    public val addAttachment: AddAttachmentDraftUseCase
        get() = AddAttachmentDraftUseCaseImpl(uploadManager, cellsConversationRepository, messageAttachmentsDraftRepository, this)

    public val removeAttachment: RemoveAttachmentDraftUseCase
        get() = RemoveAttachmentDraftUseCaseImpl(uploadManager, messageAttachmentsDraftRepository, cellsRepository)

    public val removeAttachments: RemoveAttachmentDraftsUseCase
        get() = RemoveAttachmentDraftsUseCaseImpl(messageAttachmentsDraftRepository)

    public val observeAttachments: ObserveAttachmentDraftsUseCase
        get() = ObserveAttachmentDraftsUseCaseImpl(messageAttachmentsDraftRepository, uploadManager)

    public val publishAttachments: PublishAttachmentsUseCase
        get() = PublishAttachmentsUseCaseImpl(cellsRepository)

    public val observeFiles: GetPaginatedNodesUseCase
        get() = GetPaginatedNodesUseCaseImpl(cellsRepository, cellsConversationRepository, cellAttachmentsRepository, usersRepository)

    public val observePagedFiles: GetCellFilesPagedUseCase
        get() = GetCellFilesPagedUseCaseImpl(observeFiles)

    public val downloadFile: DownloadCellFileUseCase
        get() = DownloadCellFileUseCaseImpl(cellsRepository, cellAttachmentsRepository)

    public val refreshAsset: RefreshCellAssetStateUseCase
        get() = RefreshCellAssetStateUseCaseImpl(cellsRepository, cellAttachmentsRepository)

    public val deleteAttachmentsUseCase: DeleteMessageAttachmentsUseCase
        get() = DeleteMessageAttachmentsUseCaseImpl(cellsRepository, cellAttachmentsRepository)

    public val deleteCellAssetUseCase: DeleteCellAssetUseCase
        get() = DeleteCellAssetUseCaseImpl(cellsRepository, cellAttachmentsRepository)

    public val createPublicLinkUseCase: CreatePublicLinkUseCase
        get() = CreatePublicLinkUseCaseImpl(cellClientCredentials, cellsRepository)

    public val getPublicLinkUseCase: GetPublicLinkUseCase
        get() = GetPublicLinkUseCaseImpl(cellClientCredentials, cellsRepository)

    public val deletePublicLinkUseCase: DeletePublicLinkUseCase
        get() = DeletePublicLinkUseCaseImpl(cellsRepository)

    public val retryAttachmentUpload: RetryAttachmentUploadUseCase
        get() = RetryAttachmentUploadUseCaseImpl(uploadManager, messageAttachmentsDraftRepository, this)

    public val createFolderUseCase: CreateFolderUseCase by lazy {
        CreateFolderUseCaseImpl(cellsRepository)
    }
    public val moveNodeUseCase: MoveNodeUseCase by lazy {
        MoveNodeUseCaseImpl(cellsRepository)
    }
    public val getFoldersUseCase: GetFoldersUseCase by lazy {
        GetFoldersUseCaseImpl(cellsRepository)
    }
    public val restoreNodeFromRecycleBin: RestoreNodeFromRecycleBinUseCase by lazy {
        RestoreNodeFromRecycleBinUseCaseImpl(cellsRepository, cellAttachmentsRepository)
    }
    public val getAllTags: GetAllTagsUseCase by lazy {
        GetAllTagsUseCaseImpl(cellsRepository)
    }
    public val updateNodeTagsUseCase: UpdateNodeTagsUseCase by lazy {
        UpdateNodeTagsUseCaseImpl(cellsRepository)
    }
    public val removeNodeTagsUseCase: RemoveNodeTagsUseCase by lazy {
        RemoveNodeTagsUseCaseImpl(cellsRepository)
    }
    public val renameNodeUseCase: RenameNodeUseCase by lazy {
        RenameNodeUseCaseImpl(cellsRepository, cellAttachmentsRepository)
    }

    public val isCellAvailable: IsAtLeastOneCellAvailableUseCase by lazy {
        IsAtLeastOneCellAvailableUseCaseImpl(cellsConversationRepository)
    }

    public val getMessageAttachmentUseCase: GetMessageAttachmentUseCase by lazy {
        GetMessageAttachmentUseCaseImpl(cellAttachmentsRepository)
    }

    public val getCellFileUseCase: GetCellFileUseCase by lazy {
        GetCellFileUseCaseImpl(cellsRepository)
    }

    public val createPublicLinkPasswordUseCase: CreatePublicLinkPasswordUseCase by lazy {
        CreatePublicLinkPasswordUseCaseImpl(cellsRepository)
    }

    public val updatePublicLinkPasswordUseCase: UpdatePublicLinkPasswordUseCase by lazy {
        UpdatePublicLinkPasswordUseCaseImpl(cellsRepository)
    }

    public val getPublicLinkPassword: GetPublicLinkPasswordUseCase by lazy {
        GetPublicLinkPasswordUseCaseImpl(cellsRepository)
    }

    public val setPublicLinkExpiration: SetPublicLinkExpirationUseCase by lazy {
        SetPublicLinkExpirationUseCaseImpl(cellsRepository)
    }
    public val getNodeVersions: GetNodeVersionsUseCase by lazy {
        GetNodeVersionsUseCaseImpl(cellsRepository)
    }

    public val getEditorUrl: GetEditorUrlUseCase by lazy {
        GetEditorUrlUseCaseImpl(cellsRepository)
    }
}
