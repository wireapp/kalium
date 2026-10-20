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

import com.wire.kalium.cells.domain.CellUploadCoordinator
import com.wire.kalium.cells.domain.CellUploadManager
import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.NodeServiceBuilder
import com.wire.kalium.cells.domain.usecase.AddAttachmentDraftUseCase
import com.wire.kalium.cells.domain.usecase.DeleteCellAssetUseCase
import com.wire.kalium.cells.domain.usecase.DeleteMessageAttachmentsUseCase
import com.wire.kalium.cells.domain.usecase.GetAllTagsUseCase
import com.wire.kalium.cells.domain.usecase.GetCellFileUseCase
import com.wire.kalium.cells.domain.usecase.GetCellFilesPagedUseCase
import com.wire.kalium.cells.domain.usecase.GetCellConversationsPagedUseCase
import com.wire.kalium.cells.domain.usecase.GetCellGroupConversationsUseCase
import com.wire.kalium.cells.domain.usecase.GetEditorUrlUseCase
import com.wire.kalium.cells.domain.usecase.GetPdfPreviewUrlUseCase
import com.wire.kalium.cells.domain.usecase.GetFoldersUseCase
import com.wire.kalium.cells.domain.usecase.GetMessageAttachmentUseCase
import com.wire.kalium.cells.domain.usecase.GetMessageAttachmentsUseCase
import com.wire.kalium.cells.domain.usecase.GetOwnersUseCase
import com.wire.kalium.cells.domain.usecase.GetPaginatedNodesUseCase
import com.wire.kalium.cells.domain.usecase.GetWireCellConfigurationUseCase
import com.wire.kalium.cells.domain.usecase.IsAtLeastOneCellAvailableUseCase
import com.wire.kalium.cells.domain.usecase.MoveNodeUseCase
import com.wire.kalium.cells.domain.usecase.ObserveAttachmentDraftsUseCase
import com.wire.kalium.cells.domain.usecase.PublishAttachmentsUseCase
import com.wire.kalium.cells.domain.usecase.RefreshCellAssetStateUseCase
import com.wire.kalium.cells.domain.usecase.RemoveAttachmentDraftUseCase
import com.wire.kalium.cells.domain.usecase.RemoveAttachmentDraftsUseCase
import com.wire.kalium.cells.domain.usecase.RemoveNodeTagsUseCase
import com.wire.kalium.cells.domain.usecase.RenameNodeUseCase
import com.wire.kalium.cells.domain.usecase.RestoreNodeFromRecycleBinUseCase
import com.wire.kalium.cells.domain.usecase.RetryAttachmentUploadUseCase
import com.wire.kalium.cells.domain.usecase.UpdateNodeTagsUseCase
import com.wire.kalium.cells.domain.usecase.create.CreateDocumentFileUseCase
import com.wire.kalium.cells.domain.usecase.create.CreateFolderUseCase
import com.wire.kalium.cells.domain.usecase.create.CreatePresentationFileUseCase
import com.wire.kalium.cells.domain.usecase.create.CreateSpreadsheetFileUseCase
import com.wire.kalium.cells.domain.usecase.download.DownloadCellFileUseCase
import com.wire.kalium.cells.domain.usecase.download.DownloadCellVersionUseCase
import com.wire.kalium.cells.domain.usecase.publiclink.CreatePublicLinkPasswordUseCase
import com.wire.kalium.cells.domain.usecase.publiclink.CreatePublicLinkUseCase
import com.wire.kalium.cells.domain.usecase.publiclink.DeletePublicLinkUseCase
import com.wire.kalium.cells.domain.usecase.publiclink.GetPublicLinkPasswordUseCase
import com.wire.kalium.cells.domain.usecase.publiclink.GetPublicLinkUseCase
import com.wire.kalium.cells.domain.usecase.publiclink.SetPublicLinkExpirationUseCase
import com.wire.kalium.cells.domain.usecase.publiclink.UpdatePublicLinkPasswordUseCase
import com.wire.kalium.cells.domain.usecase.versioning.GetNodeVersionsUseCase
import com.wire.kalium.cells.domain.usecase.versioning.RestoreNodeVersionUseCase
import com.wire.kalium.cells.domain.usecase.offline.DeleteOfflineFileUseCase
import com.wire.kalium.cells.domain.usecase.GetConversationNameUseCase
import com.wire.kalium.cells.domain.usecase.GetUserNameUseCase
import com.wire.kalium.cells.domain.usecase.ObserveIsAtLeastOneCellAvailableUseCase
import com.wire.kalium.cells.domain.usecase.offline.GetOfflineFileUseCase
import com.wire.kalium.cells.domain.usecase.offline.ObserveOfflineFilesUseCase
import com.wire.kalium.cells.domain.usecase.offline.ObserveOfflineFilesByConversationUseCase
import com.wire.kalium.cells.domain.usecase.offline.SaveOfflineFileUseCase
import com.wire.kalium.cells.sdk.kmp.api.NodeServiceApi
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.cellfile.CellFileDao
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentsDao
import com.wire.kalium.persistence.dao.messageattachment.MessageAttachmentDraftDao
import com.wire.kalium.persistence.dao.publiclink.PublicLinkDao
import com.wire.kalium.persistence.dao.UserConfigDAO
import com.wire.kalium.persistence.dao.member.MemberDAO
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRedirect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

public class CellsScope(
    private val cellsClient: HttpClient,
    private val dao: CellScopeDao,
    private val sessionManager: SessionManager,
    private val accessTokenApi: AccessTokenApi,
    private val userId: UserId,
    private val networkStateObserver: NetworkStateObserver,
) : CoroutineScope {

    public data class CellScopeDao(
        val attachmentDraftDao: MessageAttachmentDraftDao,
        val conversationsDao: ConversationDAO,
        val attachmentsDao: MessageAttachmentsDao,
        val cellFileDao: CellFileDao,
        val userDao: UserDAO,
        val memberDao: MemberDAO,
        val publicLinkDao: PublicLinkDao,
        val userConfigDAO: UserConfigDAO,
    )

    /**
     * A dedicated minimalist HttpClient for downloads
     */
    public val downloadHttpClient: HttpClient = HttpClient {
        followRedirects = true
        install(HttpRedirect) {
            checkHttpMethod = false
            allowHttpsDowngrade = false
        }
    }

    override val coroutineContext: CoroutineContext = SupervisorJob()

    private val bindings: LegacyCellsApi by lazy {
        createLegacyCellsGraph(
            LegacyCellsDependencies(cellsClient, dao, sessionManager, accessTokenApi, userId, networkStateObserver),
            object : CellsOwnerPorts {
                override val coroutineScope: CoroutineScope get() = this@CellsScope
                override val downloadHttpClient: HttpClient get() = this@CellsScope.downloadHttpClient
                override suspend fun getNodeServiceApi(credentials: () -> CellsCredentialsProvider): NodeServiceApi =
                    getNodeServiceApiCached(credentials)
            },
        )
    }

    /** Borrow this owner's graph; closing it remains the existing Cells/session owner's responsibility. */
    public val individualApi: KaliumCellsApi
        get() = bindings

    private val nodeServiceApiCache = CellsNodeServiceCache()

    private suspend fun getNodeServiceApiCached(credentials: () -> CellsCredentialsProvider): NodeServiceApi =
        nodeServiceApiCache.getOrCreate {
            NodeServiceBuilder.withHttpClient(cellsClient).withCredentials(credentials()).build()
        }

    public val messageAttachmentsDraftRepository: MessageAttachmentDraftRepository
        get() = bindings.cellsMessageAttachmentsDraftRepository

    public val uploadManager: CellUploadManager
        get() = bindings.cellsUploadManager

    public val uploadCoordinator: CellUploadCoordinator
        get() = bindings.cellsUploadCoordinator

    public val addAttachment: AddAttachmentDraftUseCase
        get() = bindings.cellsAddAttachment

    public val removeAttachment: RemoveAttachmentDraftUseCase
        get() = bindings.cellsRemoveAttachment

    public val removeAttachments: RemoveAttachmentDraftsUseCase
        get() = bindings.cellsRemoveAttachments

    public val observeAttachments: ObserveAttachmentDraftsUseCase
        get() = bindings.cellsObserveAttachments

    public val publishAttachments: PublishAttachmentsUseCase
        get() = bindings.cellsPublishAttachments

    public val observeFiles: GetPaginatedNodesUseCase
        get() = bindings.cellsObserveFiles

    public val observePagedFiles: GetCellFilesPagedUseCase
        get() = bindings.cellsObservePagedFiles

    public val downloadCellFile: DownloadCellFileUseCase
        get() = bindings.cellsDownloadCellFile

    public val refreshAsset: RefreshCellAssetStateUseCase
        get() = bindings.cellsRefreshAsset

    public val deleteAttachmentsUseCase: DeleteMessageAttachmentsUseCase
        get() = bindings.cellsDeleteAttachmentsUseCase

    public val deleteCellAssetUseCase: DeleteCellAssetUseCase
        get() = bindings.cellsDeleteCellAssetUseCase

    public val createPublicLinkUseCase: CreatePublicLinkUseCase
        get() = bindings.cellsCreatePublicLinkUseCase

    public val getPublicLinkUseCase: GetPublicLinkUseCase
        get() = bindings.cellsGetPublicLinkUseCase

    public val deletePublicLinkUseCase: DeletePublicLinkUseCase
        get() = bindings.cellsDeletePublicLinkUseCase

    public val retryAttachmentUpload: RetryAttachmentUploadUseCase
        get() = bindings.cellsRetryAttachmentUpload

    public val createFolderUseCase: CreateFolderUseCase
        get() = bindings.cellsCreateFolderUseCase
    public val createSpreadsheetFileUseCase: CreateSpreadsheetFileUseCase
        get() = bindings.cellsCreateSpreadsheetFileUseCase
    public val createPresentationFileUseCase: CreatePresentationFileUseCase
        get() = bindings.cellsCreatePresentationFileUseCase
    public val createDocumentFileUseCase: CreateDocumentFileUseCase
        get() = bindings.cellsCreateDocumentFileUseCase
    public val moveNodeUseCase: MoveNodeUseCase
        get() = bindings.cellsMoveNodeUseCase
    public val getFoldersUseCase: GetFoldersUseCase
        get() = bindings.cellsGetFoldersUseCase
    public val restoreNodeFromRecycleBin: RestoreNodeFromRecycleBinUseCase
        get() = bindings.cellsRestoreNodeFromRecycleBin
    public val getAllTags: GetAllTagsUseCase
        get() = bindings.cellsGetAllTags
    public val updateNodeTagsUseCase: UpdateNodeTagsUseCase
        get() = bindings.cellsUpdateNodeTagsUseCase
    public val removeNodeTagsUseCase: RemoveNodeTagsUseCase
        get() = bindings.cellsRemoveNodeTagsUseCase
    public val renameNodeUseCase: RenameNodeUseCase
        get() = bindings.cellsRenameNodeUseCase

    public val isCellAvailable: IsAtLeastOneCellAvailableUseCase
        get() = bindings.cellsIsCellAvailable
    public val observeIsCellAvailable: ObserveIsAtLeastOneCellAvailableUseCase
        get() = bindings.cellsObserveIsCellAvailable

    public val getMessageAttachmentUseCase: GetMessageAttachmentUseCase
        get() = bindings.cellsGetMessageAttachmentUseCase
    public val getOwnersUseCase: GetOwnersUseCase
        get() = bindings.cellsGetOwnersUseCase

    public val getCellGroupConversationsUseCase: GetCellGroupConversationsUseCase
        get() = bindings.cellsGetCellGroupConversationsUseCase

    public val getCellConversationsPagedUseCase: GetCellConversationsPagedUseCase
        get() = bindings.cellsGetCellConversationsPagedUseCase

    public val getMessageAttachmentsUseCase: GetMessageAttachmentsUseCase
        get() = bindings.cellsGetMessageAttachmentsUseCase

    public val getCellFileUseCase: GetCellFileUseCase
        get() = bindings.cellsGetCellFileUseCase

    public val createPublicLinkPasswordUseCase: CreatePublicLinkPasswordUseCase
        get() = bindings.cellsCreatePublicLinkPasswordUseCase

    public val updatePublicLinkPasswordUseCase: UpdatePublicLinkPasswordUseCase
        get() = bindings.cellsUpdatePublicLinkPasswordUseCase

    public val getPublicLinkPassword: GetPublicLinkPasswordUseCase
        get() = bindings.cellsGetPublicLinkPassword

    public val setPublicLinkExpiration: SetPublicLinkExpirationUseCase
        get() = bindings.cellsSetPublicLinkExpiration

    public val getEditorUrl: GetEditorUrlUseCase
        get() = bindings.cellsGetEditorUrl

    public val getPdfPreviewUrl: GetPdfPreviewUrlUseCase
        get() = bindings.cellsGetPdfPreviewUrl

    public val getNodeVersions: GetNodeVersionsUseCase
        get() = bindings.cellsGetNodeVersions

    public val restoreNodeVersion: RestoreNodeVersionUseCase
        get() = bindings.cellsRestoreNodeVersion
    public val downloadCellVersion: DownloadCellVersionUseCase
        get() = bindings.cellsDownloadCellVersion

    public val getCellConfig: GetWireCellConfigurationUseCase
        get() = bindings.cellsGetCellConfig

    public val saveOfflineFile: SaveOfflineFileUseCase
        get() = bindings.cellsSaveOfflineFile

    public val deleteOfflineFile: DeleteOfflineFileUseCase
        get() = bindings.cellsDeleteOfflineFile

    public val observeOfflineFiles: ObserveOfflineFilesUseCase
        get() = bindings.cellsObserveOfflineFiles

    public val observeOfflineFilesByConversation: ObserveOfflineFilesByConversationUseCase
        get() = bindings.cellsObserveOfflineFilesByConversation

    public val getOfflineFile: GetOfflineFileUseCase
        get() = bindings.cellsGetOfflineFile

    public val getConversationName: GetConversationNameUseCase
        get() = bindings.cellsGetConversationName

    public val getUserName: GetUserNameUseCase
        get() = bindings.cellsGetUserName
}
