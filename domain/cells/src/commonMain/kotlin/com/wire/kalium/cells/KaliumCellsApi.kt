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

package com.wire.kalium.cells

import com.wire.kalium.cells.domain.CellUploadCoordinator
import com.wire.kalium.cells.domain.CellUploadManager
import com.wire.kalium.cells.domain.usecase.AddAttachmentDraftUseCase
import com.wire.kalium.cells.domain.usecase.DeleteCellAssetUseCase
import com.wire.kalium.cells.domain.usecase.GetAllTagsUseCase
import com.wire.kalium.cells.domain.usecase.GetCellConversationsPagedUseCase
import com.wire.kalium.cells.domain.usecase.GetCellFileUseCase
import com.wire.kalium.cells.domain.usecase.GetCellFilesPagedUseCase
import com.wire.kalium.cells.domain.usecase.GetConversationNameUseCase
import com.wire.kalium.cells.domain.usecase.GetEditorUrlUseCase
import com.wire.kalium.cells.domain.usecase.GetFoldersUseCase
import com.wire.kalium.cells.domain.usecase.GetMessageAttachmentUseCase
import com.wire.kalium.cells.domain.usecase.GetOwnersUseCase
import com.wire.kalium.cells.domain.usecase.GetPaginatedNodesUseCase
import com.wire.kalium.cells.domain.usecase.GetPdfPreviewUrlUseCase
import com.wire.kalium.cells.domain.usecase.GetUserNameUseCase
import com.wire.kalium.cells.domain.usecase.GetWireCellConfigurationUseCase
import com.wire.kalium.cells.domain.usecase.IsAtLeastOneCellAvailableUseCase
import com.wire.kalium.cells.domain.usecase.MoveNodeUseCase
import com.wire.kalium.cells.domain.usecase.ObserveAttachmentDraftsUseCase
import com.wire.kalium.cells.domain.usecase.ObserveIsAtLeastOneCellAvailableUseCase
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
import com.wire.kalium.cells.domain.usecase.offline.DeleteOfflineFileUseCase
import com.wire.kalium.cells.domain.usecase.offline.GetOfflineFileUseCase
import com.wire.kalium.cells.domain.usecase.offline.ObserveOfflineFilesByConversationUseCase
import com.wire.kalium.cells.domain.usecase.offline.ObserveOfflineFilesUseCase
import com.wire.kalium.cells.domain.usecase.offline.SaveOfflineFileUseCase
import com.wire.kalium.cells.domain.usecase.publiclink.CreatePublicLinkPasswordUseCase
import com.wire.kalium.cells.domain.usecase.publiclink.CreatePublicLinkUseCase
import com.wire.kalium.cells.domain.usecase.publiclink.DeletePublicLinkUseCase
import com.wire.kalium.cells.domain.usecase.publiclink.GetPublicLinkPasswordUseCase
import com.wire.kalium.cells.domain.usecase.publiclink.GetPublicLinkUseCase
import com.wire.kalium.cells.domain.usecase.publiclink.SetPublicLinkExpirationUseCase
import com.wire.kalium.cells.domain.usecase.publiclink.UpdatePublicLinkPasswordUseCase
import com.wire.kalium.cells.domain.usecase.versioning.GetNodeVersionsUseCase
import com.wire.kalium.cells.domain.usecase.versioning.RestoreNodeVersionUseCase

/** Individual bindings preserve their existing owner; this API does not create a consumer. */
public interface KaliumCellsApi {
    public val cellsAddAttachment: AddAttachmentDraftUseCase
    public val cellsRemoveAttachment: RemoveAttachmentDraftUseCase
    public val cellsRemoveAttachments: RemoveAttachmentDraftsUseCase
    public val cellsObserveAttachments: ObserveAttachmentDraftsUseCase
    public val cellsPublishAttachments: PublishAttachmentsUseCase
    public val cellsUploadManager: CellUploadManager
    public val cellsUploadCoordinator: CellUploadCoordinator
    public val cellsObserveFiles: GetPaginatedNodesUseCase
    public val cellsDownloadCellFile: DownloadCellFileUseCase
    public val cellsRefreshAsset: RefreshCellAssetStateUseCase
    public val cellsDeleteCellAssetUseCase: DeleteCellAssetUseCase
    public val cellsCreatePublicLinkUseCase: CreatePublicLinkUseCase
    public val cellsGetPublicLinkUseCase: GetPublicLinkUseCase
    public val cellsDeletePublicLinkUseCase: DeletePublicLinkUseCase
    public val cellsRetryAttachmentUpload: RetryAttachmentUploadUseCase
    public val cellsCreateFolderUseCase: CreateFolderUseCase
    public val cellsCreateSpreadsheetFileUseCase: CreateSpreadsheetFileUseCase
    public val cellsCreateDocumentFileUseCase: CreateDocumentFileUseCase
    public val cellsCreatePresentationFileUseCase: CreatePresentationFileUseCase
    public val cellsMoveNodeUseCase: MoveNodeUseCase
    public val cellsGetFoldersUseCase: GetFoldersUseCase
    public val cellsRestoreNodeFromRecycleBin: RestoreNodeFromRecycleBinUseCase
    public val cellsRenameNodeUseCase: RenameNodeUseCase
    public val cellsGetAllTags: GetAllTagsUseCase
    public val cellsUpdateNodeTagsUseCase: UpdateNodeTagsUseCase
    public val cellsRemoveNodeTagsUseCase: RemoveNodeTagsUseCase
    public val cellsIsCellAvailable: IsAtLeastOneCellAvailableUseCase
    public val cellsObserveIsCellAvailable: ObserveIsAtLeastOneCellAvailableUseCase
    public val cellsGetMessageAttachmentUseCase: GetMessageAttachmentUseCase
    public val cellsGetOwnersUseCase: GetOwnersUseCase
    public val cellsGetCellFileUseCase: GetCellFileUseCase
    public val cellsCreatePublicLinkPasswordUseCase: CreatePublicLinkPasswordUseCase
    public val cellsUpdatePublicLinkPasswordUseCase: UpdatePublicLinkPasswordUseCase
    public val cellsGetPublicLinkPassword: GetPublicLinkPasswordUseCase
    public val cellsSetPublicLinkExpiration: SetPublicLinkExpirationUseCase
    public val cellsGetEditorUrl: GetEditorUrlUseCase
    public val cellsGetPdfPreviewUrl: GetPdfPreviewUrlUseCase
    public val cellsGetNodeVersions: GetNodeVersionsUseCase
    public val cellsRestoreNodeVersion: RestoreNodeVersionUseCase
    public val cellsDownloadCellVersion: DownloadCellVersionUseCase
    public val cellsGetCellConfig: GetWireCellConfigurationUseCase
    public val cellsSaveOfflineFile: SaveOfflineFileUseCase
    public val cellsDeleteOfflineFile: DeleteOfflineFileUseCase
    public val cellsObserveOfflineFiles: ObserveOfflineFilesUseCase
    public val cellsObserveOfflineFilesByConversation: ObserveOfflineFilesByConversationUseCase
    public val cellsGetOfflineFile: GetOfflineFileUseCase
    public val cellsGetConversationName: GetConversationNameUseCase
    public val cellsGetUserName: GetUserNameUseCase
    public val cellsObservePagedFiles: GetCellFilesPagedUseCase
    public val cellsGetCellConversationsPagedUseCase: GetCellConversationsPagedUseCase
}
