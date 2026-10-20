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
import com.wire.kalium.cells.data.CellConfigDataSource
import com.wire.kalium.cells.data.CellConversationDataSource
import com.wire.kalium.cells.data.CellFileDataSource
import com.wire.kalium.cells.data.CellUploadCoordinatorImpl
import com.wire.kalium.cells.data.CellUploadManagerImpl
import com.wire.kalium.cells.data.CellUsersDataSource
import com.wire.kalium.cells.data.CellsApiImpl
import com.wire.kalium.cells.data.CellsAwsClient
import com.wire.kalium.cells.data.CellsDataSource
import com.wire.kalium.cells.data.FileDownloader
import com.wire.kalium.cells.data.MessageAttachmentDraftDataSource
import com.wire.kalium.cells.data.cellsAwsClient
import com.wire.kalium.cells.data.fileDownloader
import com.wire.kalium.cells.domain.CellAttachmentsRepository
import com.wire.kalium.cells.domain.CellConfigRepository
import com.wire.kalium.cells.domain.CellConversationRepository
import com.wire.kalium.cells.domain.CellFileRepository
import com.wire.kalium.cells.domain.CellUploadCoordinator
import com.wire.kalium.cells.domain.CellUploadManager
import com.wire.kalium.cells.domain.CellUsersRepository
import com.wire.kalium.cells.domain.CellsApi
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.SelfTeamIdProvider
import com.wire.kalium.cells.domain.SelfTeamIdProviderImpl
import com.wire.kalium.cells.domain.usecase.AddAttachmentDraftUseCase
import com.wire.kalium.cells.domain.usecase.AddAttachmentDraftUseCaseImpl
import com.wire.kalium.cells.domain.usecase.DeleteCellAssetUseCase
import com.wire.kalium.cells.domain.usecase.DeleteCellAssetUseCaseImpl
import com.wire.kalium.cells.domain.usecase.DeleteMessageAttachmentsUseCase
import com.wire.kalium.cells.domain.usecase.DeleteMessageAttachmentsUseCaseImpl
import com.wire.kalium.cells.domain.usecase.GetAllTagsUseCase
import com.wire.kalium.cells.domain.usecase.GetAllTagsUseCaseImpl
import com.wire.kalium.cells.domain.usecase.GetCellConversationsPagedUseCase
import com.wire.kalium.cells.domain.usecase.GetCellConversationsPagedUseCaseImpl
import com.wire.kalium.cells.domain.usecase.GetCellFileUseCase
import com.wire.kalium.cells.domain.usecase.GetCellFileUseCaseImpl
import com.wire.kalium.cells.domain.usecase.GetCellFilesPagedUseCase
import com.wire.kalium.cells.domain.usecase.GetCellFilesPagedUseCaseImpl
import com.wire.kalium.cells.domain.usecase.GetCellGroupConversationsUseCase
import com.wire.kalium.cells.domain.usecase.GetCellGroupConversationsUseCaseImpl
import com.wire.kalium.cells.domain.usecase.GetConversationNameUseCase
import com.wire.kalium.cells.domain.usecase.GetConversationNameUseCaseImpl
import com.wire.kalium.cells.domain.usecase.GetEditorUrlUseCase
import com.wire.kalium.cells.domain.usecase.GetEditorUrlUseCaseImpl
import com.wire.kalium.cells.domain.usecase.GetFoldersUseCase
import com.wire.kalium.cells.domain.usecase.GetFoldersUseCaseImpl
import com.wire.kalium.cells.domain.usecase.GetMessageAttachmentUseCase
import com.wire.kalium.cells.domain.usecase.GetMessageAttachmentUseCaseImpl
import com.wire.kalium.cells.domain.usecase.GetMessageAttachmentsUseCase
import com.wire.kalium.cells.domain.usecase.GetMessageAttachmentsUseCaseImpl
import com.wire.kalium.cells.domain.usecase.GetOwnersUseCase
import com.wire.kalium.cells.domain.usecase.GetOwnersUseCaseImpl
import com.wire.kalium.cells.domain.usecase.GetPaginatedNodesUseCase
import com.wire.kalium.cells.domain.usecase.GetPaginatedNodesUseCaseImpl
import com.wire.kalium.cells.domain.usecase.GetPdfPreviewUrlUseCase
import com.wire.kalium.cells.domain.usecase.GetPdfPreviewUrlUseCaseImpl
import com.wire.kalium.cells.domain.usecase.GetUserNameUseCase
import com.wire.kalium.cells.domain.usecase.GetUserNameUseCaseImpl
import com.wire.kalium.cells.domain.usecase.GetWireCellConfigurationUseCase
import com.wire.kalium.cells.domain.usecase.GetWireCellConfigurationUseCaseImpl
import com.wire.kalium.cells.domain.usecase.IsAtLeastOneCellAvailableUseCase
import com.wire.kalium.cells.domain.usecase.IsAtLeastOneCellAvailableUseCaseImpl
import com.wire.kalium.cells.domain.usecase.MoveNodeUseCase
import com.wire.kalium.cells.domain.usecase.MoveNodeUseCaseImpl
import com.wire.kalium.cells.domain.usecase.ObserveAttachmentDraftsUseCase
import com.wire.kalium.cells.domain.usecase.ObserveAttachmentDraftsUseCaseImpl
import com.wire.kalium.cells.domain.usecase.ObserveIsAtLeastOneCellAvailableUseCase
import com.wire.kalium.cells.domain.usecase.ObserveIsAtLeastOneCellAvailableUseCaseImpl
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
import com.wire.kalium.cells.domain.usecase.create.CreateDocumentFileUseCase
import com.wire.kalium.cells.domain.usecase.create.CreateDocumentFileUseCaseImpl
import com.wire.kalium.cells.domain.usecase.create.CreateFolderUseCase
import com.wire.kalium.cells.domain.usecase.create.CreateFolderUseCaseImpl
import com.wire.kalium.cells.domain.usecase.create.CreatePresentationFileUseCase
import com.wire.kalium.cells.domain.usecase.create.CreatePresentationFileUseCaseImpl
import com.wire.kalium.cells.domain.usecase.create.CreateSpreadsheetFileUseCase
import com.wire.kalium.cells.domain.usecase.create.CreateSpreadsheetFileUseCaseImpl
import com.wire.kalium.cells.domain.usecase.download.DownloadCellFileUseCase
import com.wire.kalium.cells.domain.usecase.download.DownloadCellFileUseCaseImpl
import com.wire.kalium.cells.domain.usecase.download.DownloadCellVersionUseCase
import com.wire.kalium.cells.domain.usecase.download.DownloadCellVersionUseCaseImpl
import com.wire.kalium.cells.domain.usecase.offline.DeleteOfflineFileUseCase
import com.wire.kalium.cells.domain.usecase.offline.DeleteOfflineFileUseCaseImpl
import com.wire.kalium.cells.domain.usecase.offline.GetOfflineFileUseCase
import com.wire.kalium.cells.domain.usecase.offline.GetOfflineFileUseCaseImpl
import com.wire.kalium.cells.domain.usecase.offline.ObserveOfflineFilesByConversationUseCase
import com.wire.kalium.cells.domain.usecase.offline.ObserveOfflineFilesByConversationUseCaseImpl
import com.wire.kalium.cells.domain.usecase.offline.ObserveOfflineFilesUseCase
import com.wire.kalium.cells.domain.usecase.offline.ObserveOfflineFilesUseCaseImpl
import com.wire.kalium.cells.domain.usecase.offline.SaveOfflineFileUseCase
import com.wire.kalium.cells.domain.usecase.offline.SaveOfflineFileUseCaseImpl
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
import com.wire.kalium.cells.domain.usecase.versioning.RestoreNodeVersionUseCase
import com.wire.kalium.cells.domain.usecase.versioning.RestoreNodeVersionUseCaseImpl
import com.wire.kalium.cells.sdk.kmp.api.NodeServiceApi
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.session.SessionManager
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import okio.FileSystem
import okio.SYSTEM

internal abstract class CellsFacadeLifetime private constructor()

@Qualifier
internal annotation class LegacyCellsBinding(val value: String)

internal class LegacyCellsDependencies(
    val cellsClient: HttpClient,
    val dao: CellsScope.CellScopeDao,
    val sessionManager: SessionManager,
    val accessTokenApi: AccessTokenApi,
    val userId: UserId,
    val networkStateObserver: NetworkStateObserver,
)

/** The facade owns its job, download client and node cache; graph bindings borrow these ports. */
internal interface CellsOwnerPorts {
    val coroutineScope: CoroutineScope
    val downloadHttpClient: HttpClient
    suspend fun getNodeServiceApi(credentials: () -> CellsCredentialsProvider): NodeServiceApi
}

internal interface LegacyCellsApi : KaliumCellsApi {
    @get:LegacyCellsBinding("cellsCredentialsProvider")
    val cellsCellsCredentialsProvider: CellsCredentialsProvider

    @get:LegacyCellsBinding("cellAwsClient")
    val cellsCellAwsClient: CellsAwsClient

    @get:LegacyCellsBinding("cellsApi")
    val cellsCellsApi: CellsApi

    @get:LegacyCellsBinding("cellsRepository")
    val cellsCellsRepository: CellsRepository

    @get:LegacyCellsBinding("cellsConversationRepository")
    val cellsCellsConversationRepository: CellConversationRepository

    @get:LegacyCellsBinding("cellAttachmentsRepository")
    val cellsCellAttachmentsRepository: CellAttachmentsRepository

    @get:LegacyCellsBinding("messageAttachmentsDraftRepository")
    val cellsMessageAttachmentsDraftRepository: MessageAttachmentDraftRepository

    @get:LegacyCellsBinding("usersRepository")
    val cellsUsersRepository: CellUsersRepository

    @get:LegacyCellsBinding("cellConfigRepository")
    val cellsCellConfigRepository: CellConfigRepository

    @get:LegacyCellsBinding("cellFileRepository")
    val cellsCellFileRepository: CellFileRepository

    @get:LegacyCellsBinding("uploadManager")
    override val cellsUploadManager: CellUploadManager

    @get:LegacyCellsBinding("uploadCoordinator")
    override val cellsUploadCoordinator: CellUploadCoordinator

    @get:LegacyCellsBinding("addAttachment")
    override val cellsAddAttachment: AddAttachmentDraftUseCase

    @get:LegacyCellsBinding("removeAttachment")
    override val cellsRemoveAttachment: RemoveAttachmentDraftUseCase

    @get:LegacyCellsBinding("removeAttachments")
    override val cellsRemoveAttachments: RemoveAttachmentDraftsUseCase

    @get:LegacyCellsBinding("observeAttachments")
    override val cellsObserveAttachments: ObserveAttachmentDraftsUseCase

    @get:LegacyCellsBinding("publishAttachments")
    override val cellsPublishAttachments: PublishAttachmentsUseCase

    @get:LegacyCellsBinding("selfTeamIdProvider")
    val cellsSelfTeamIdProvider: SelfTeamIdProvider

    @get:LegacyCellsBinding("observeFiles")
    override val cellsObserveFiles: GetPaginatedNodesUseCase

    @get:LegacyCellsBinding("observePagedFiles")
    override val cellsObservePagedFiles: GetCellFilesPagedUseCase

    @get:LegacyCellsBinding("downloadCellFile")
    override val cellsDownloadCellFile: DownloadCellFileUseCase

    @get:LegacyCellsBinding("fileDownloader")
    val cellsFileDownloader: FileDownloader

    @get:LegacyCellsBinding("refreshAsset")
    override val cellsRefreshAsset: RefreshCellAssetStateUseCase

    @get:LegacyCellsBinding("deleteAttachmentsUseCase")
    val cellsDeleteAttachmentsUseCase: DeleteMessageAttachmentsUseCase

    @get:LegacyCellsBinding("deleteCellAssetUseCase")
    override val cellsDeleteCellAssetUseCase: DeleteCellAssetUseCase

    @get:LegacyCellsBinding("createPublicLinkUseCase")
    override val cellsCreatePublicLinkUseCase: CreatePublicLinkUseCase

    @get:LegacyCellsBinding("getPublicLinkUseCase")
    override val cellsGetPublicLinkUseCase: GetPublicLinkUseCase

    @get:LegacyCellsBinding("deletePublicLinkUseCase")
    override val cellsDeletePublicLinkUseCase: DeletePublicLinkUseCase

    @get:LegacyCellsBinding("retryAttachmentUpload")
    override val cellsRetryAttachmentUpload: RetryAttachmentUploadUseCase

    @get:LegacyCellsBinding("createFolderUseCase")
    override val cellsCreateFolderUseCase: CreateFolderUseCase

    @get:LegacyCellsBinding("createSpreadsheetFileUseCase")
    override val cellsCreateSpreadsheetFileUseCase: CreateSpreadsheetFileUseCase

    @get:LegacyCellsBinding("createPresentationFileUseCase")
    override val cellsCreatePresentationFileUseCase: CreatePresentationFileUseCase

    @get:LegacyCellsBinding("createDocumentFileUseCase")
    override val cellsCreateDocumentFileUseCase: CreateDocumentFileUseCase

    @get:LegacyCellsBinding("moveNodeUseCase")
    override val cellsMoveNodeUseCase: MoveNodeUseCase

    @get:LegacyCellsBinding("getFoldersUseCase")
    override val cellsGetFoldersUseCase: GetFoldersUseCase

    @get:LegacyCellsBinding("restoreNodeFromRecycleBin")
    override val cellsRestoreNodeFromRecycleBin: RestoreNodeFromRecycleBinUseCase

    @get:LegacyCellsBinding("getAllTags")
    override val cellsGetAllTags: GetAllTagsUseCase

    @get:LegacyCellsBinding("updateNodeTagsUseCase")
    override val cellsUpdateNodeTagsUseCase: UpdateNodeTagsUseCase

    @get:LegacyCellsBinding("removeNodeTagsUseCase")
    override val cellsRemoveNodeTagsUseCase: RemoveNodeTagsUseCase

    @get:LegacyCellsBinding("renameNodeUseCase")
    override val cellsRenameNodeUseCase: RenameNodeUseCase

    @get:LegacyCellsBinding("isCellAvailable")
    override val cellsIsCellAvailable: IsAtLeastOneCellAvailableUseCase

    @get:LegacyCellsBinding("observeIsCellAvailable")
    override val cellsObserveIsCellAvailable: ObserveIsAtLeastOneCellAvailableUseCase

    @get:LegacyCellsBinding("getMessageAttachmentUseCase")
    override val cellsGetMessageAttachmentUseCase: GetMessageAttachmentUseCase

    @get:LegacyCellsBinding("getOwnersUseCase")
    override val cellsGetOwnersUseCase: GetOwnersUseCase

    @get:LegacyCellsBinding("getCellGroupConversationsUseCase")
    val cellsGetCellGroupConversationsUseCase: GetCellGroupConversationsUseCase

    @get:LegacyCellsBinding("getCellConversationsPagedUseCase")
    override val cellsGetCellConversationsPagedUseCase: GetCellConversationsPagedUseCase

    @get:LegacyCellsBinding("getMessageAttachmentsUseCase")
    val cellsGetMessageAttachmentsUseCase: GetMessageAttachmentsUseCase

    @get:LegacyCellsBinding("getCellFileUseCase")
    override val cellsGetCellFileUseCase: GetCellFileUseCase

    @get:LegacyCellsBinding("createPublicLinkPasswordUseCase")
    override val cellsCreatePublicLinkPasswordUseCase: CreatePublicLinkPasswordUseCase

    @get:LegacyCellsBinding("updatePublicLinkPasswordUseCase")
    override val cellsUpdatePublicLinkPasswordUseCase: UpdatePublicLinkPasswordUseCase

    @get:LegacyCellsBinding("getPublicLinkPassword")
    override val cellsGetPublicLinkPassword: GetPublicLinkPasswordUseCase

    @get:LegacyCellsBinding("setPublicLinkExpiration")
    override val cellsSetPublicLinkExpiration: SetPublicLinkExpirationUseCase

    @get:LegacyCellsBinding("getEditorUrl")
    override val cellsGetEditorUrl: GetEditorUrlUseCase

    @get:LegacyCellsBinding("getPdfPreviewUrl")
    override val cellsGetPdfPreviewUrl: GetPdfPreviewUrlUseCase

    @get:LegacyCellsBinding("getNodeVersions")
    override val cellsGetNodeVersions: GetNodeVersionsUseCase

    @get:LegacyCellsBinding("restoreNodeVersion")
    override val cellsRestoreNodeVersion: RestoreNodeVersionUseCase

    @get:LegacyCellsBinding("downloadCellVersion")
    override val cellsDownloadCellVersion: DownloadCellVersionUseCase

    @get:LegacyCellsBinding("getCellConfig")
    override val cellsGetCellConfig: GetWireCellConfigurationUseCase

    @get:LegacyCellsBinding("saveOfflineFile")
    override val cellsSaveOfflineFile: SaveOfflineFileUseCase

    @get:LegacyCellsBinding("deleteOfflineFile")
    override val cellsDeleteOfflineFile: DeleteOfflineFileUseCase

    @get:LegacyCellsBinding("observeOfflineFiles")
    override val cellsObserveOfflineFiles: ObserveOfflineFilesUseCase

    @get:LegacyCellsBinding("observeOfflineFilesByConversation")
    override val cellsObserveOfflineFilesByConversation: ObserveOfflineFilesByConversationUseCase

    @get:LegacyCellsBinding("getOfflineFile")
    override val cellsGetOfflineFile: GetOfflineFileUseCase

    @get:LegacyCellsBinding("getConversationName")
    override val cellsGetConversationName: GetConversationNameUseCase

    @get:LegacyCellsBinding("getUserName")
    override val cellsGetUserName: GetUserNameUseCase
}

/** Canonical common contract; final annotated roots live in the platform leaf source sets. */
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
internal interface LegacyCellsBindings : LegacyCellsApi {
    @Provides
    @LegacyCellsBinding("cellsClient")
    fun provideCellsCellsClient(dependencies: LegacyCellsDependencies): HttpClient = dependencies.cellsClient

    @Provides
    @LegacyCellsBinding("dao")
    fun provideCellsDao(dependencies: LegacyCellsDependencies): CellsScope.CellScopeDao = dependencies.dao

    @Provides
    @LegacyCellsBinding("sessionManager")
    fun provideCellsSessionManager(dependencies: LegacyCellsDependencies): SessionManager = dependencies.sessionManager

    @Provides
    @LegacyCellsBinding("accessTokenApi")
    fun provideCellsAccessTokenApi(dependencies: LegacyCellsDependencies): AccessTokenApi = dependencies.accessTokenApi

    @Provides
    @LegacyCellsBinding("userId")
    fun provideCellsUserId(dependencies: LegacyCellsDependencies): UserId = dependencies.userId

    @Provides
    @LegacyCellsBinding("networkStateObserver")
    fun provideCellsNetworkStateObserver(dependencies: LegacyCellsDependencies): NetworkStateObserver = dependencies.networkStateObserver

    @Provides
    @LegacyCellsBinding("coroutineScope")
    fun provideCellsCoroutineScope(owner: CellsOwnerPorts): CoroutineScope = owner.coroutineScope

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("cellsCredentialsProvider")
    fun provideCellsCellsCredentialsProvider(
        @LegacyCellsBinding("getCellConfig")
        getCellConfig: Lazy<GetWireCellConfigurationUseCase>,
    ): CellsCredentialsProvider =
        CellsCredentialsProviderImpl(getCellConfig.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("cellAwsClient")
    fun provideCellsCellAwsClient(
        @LegacyCellsBinding("sessionManager")
        sessionManager: SessionManager,
        @LegacyCellsBinding("accessTokenApi")
        accessTokenApi: AccessTokenApi,
        @LegacyCellsBinding("cellsCredentialsProvider")
        cellsCredentialsProvider: Lazy<CellsCredentialsProvider>,
    ): CellsAwsClient =
        cellsAwsClient(cellsCredentialsProvider.value, sessionManager, accessTokenApi)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("cellsApi")
    fun provideCellsCellsApi(
        @LegacyCellsBinding("cellsCredentialsProvider")
        cellsCredentialsProvider: Lazy<CellsCredentialsProvider>,
        owner: CellsOwnerPorts,
    ): CellsApi =
        CellsApiImpl(getNodeServiceApi = { owner.getNodeServiceApi { cellsCredentialsProvider.value } })

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("cellsRepository")
    fun provideCellsCellsRepository(
        @LegacyCellsBinding("dao")
        dao: CellsScope.CellScopeDao,
        @LegacyCellsBinding("cellAwsClient")
        cellAwsClient: Lazy<CellsAwsClient>,
        @LegacyCellsBinding("cellsApi")
        cellsApi: Lazy<CellsApi>,
    ): CellsRepository =
        CellsDataSource(
            cellsApi = cellsApi.value,
            publicLinkDao = dao.publicLinkDao,
            awsClient = cellAwsClient.value,
            fileSystem = FileSystem.SYSTEM
        )

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("cellsConversationRepository")
    fun provideCellsCellsConversationRepository(
        @LegacyCellsBinding("dao")
        dao: CellsScope.CellScopeDao,
    ): CellConversationRepository =
        CellConversationDataSource(dao.conversationsDao)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("cellAttachmentsRepository")
    fun provideCellsCellAttachmentsRepository(
        @LegacyCellsBinding("dao")
        dao: CellsScope.CellScopeDao,
    ): CellAttachmentsRepository =
        CellAttachmentsDataSource(dao.attachmentsDao, dao.cellFileDao)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("messageAttachmentsDraftRepository")
    fun provideCellsMessageAttachmentsDraftRepository(
        @LegacyCellsBinding("dao")
        dao: CellsScope.CellScopeDao,
    ): MessageAttachmentDraftRepository =
        MessageAttachmentDraftDataSource(dao.attachmentDraftDao)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("usersRepository")
    fun provideCellsUsersRepository(
        @LegacyCellsBinding("dao")
        dao: CellsScope.CellScopeDao,
    ): CellUsersRepository =
        CellUsersDataSource(dao.userDao, dao.memberDao)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("cellConfigRepository")
    fun provideCellsCellConfigRepository(
        @LegacyCellsBinding("dao")
        dao: CellsScope.CellScopeDao,
    ): CellConfigRepository =
        CellConfigDataSource(dao.userConfigDAO)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("cellFileRepository")
    fun provideCellsCellFileRepository(
        @LegacyCellsBinding("dao")
        dao: CellsScope.CellScopeDao,
    ): CellFileRepository =
        CellFileDataSource(dao.cellFileDao)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("uploadManager")
    fun provideCellsUploadManager(
        @LegacyCellsBinding("coroutineScope")
        coroutineScope: CoroutineScope,
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
    ): CellUploadManager =
        CellUploadManagerImpl(
            repository = cellsRepository.value,
            uploadScope = coroutineScope,
        )

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("uploadCoordinator")
    fun provideCellsUploadCoordinator(
        @LegacyCellsBinding("networkStateObserver")
        networkStateObserver: NetworkStateObserver,
        @LegacyCellsBinding("coroutineScope")
        coroutineScope: CoroutineScope,
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
        @LegacyCellsBinding("uploadManager")
        uploadManager: Lazy<CellUploadManager>,
    ): CellUploadCoordinator =
        CellUploadCoordinatorImpl(
            uploadManager = uploadManager.value,
            cellsRepository = cellsRepository.value,
            scope = coroutineScope,
            networkStateObserver = networkStateObserver,
        )

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("addAttachment")
    fun provideCellsAddAttachment(
        @LegacyCellsBinding("coroutineScope")
        coroutineScope: CoroutineScope,
        @LegacyCellsBinding("cellsConversationRepository")
        cellsConversationRepository: Lazy<CellConversationRepository>,
        @LegacyCellsBinding("messageAttachmentsDraftRepository")
        messageAttachmentsDraftRepository: Lazy<MessageAttachmentDraftRepository>,
        @LegacyCellsBinding("uploadManager")
        uploadManager: Lazy<CellUploadManager>,
    ): AddAttachmentDraftUseCase =
        AddAttachmentDraftUseCaseImpl(
            uploadManager.value,
            cellsConversationRepository.value,
            messageAttachmentsDraftRepository.value,
            coroutineScope,
        )

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("removeAttachment")
    fun provideCellsRemoveAttachment(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
        @LegacyCellsBinding("messageAttachmentsDraftRepository")
        messageAttachmentsDraftRepository: Lazy<MessageAttachmentDraftRepository>,
        @LegacyCellsBinding("uploadManager")
        uploadManager: Lazy<CellUploadManager>,
    ): RemoveAttachmentDraftUseCase =
        RemoveAttachmentDraftUseCaseImpl(uploadManager.value, messageAttachmentsDraftRepository.value, cellsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("removeAttachments")
    fun provideCellsRemoveAttachments(
        @LegacyCellsBinding("messageAttachmentsDraftRepository")
        messageAttachmentsDraftRepository: Lazy<MessageAttachmentDraftRepository>,
    ): RemoveAttachmentDraftsUseCase =
        RemoveAttachmentDraftsUseCaseImpl(messageAttachmentsDraftRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("observeAttachments")
    fun provideCellsObserveAttachments(
        @LegacyCellsBinding("messageAttachmentsDraftRepository")
        messageAttachmentsDraftRepository: Lazy<MessageAttachmentDraftRepository>,
        @LegacyCellsBinding("uploadManager")
        uploadManager: Lazy<CellUploadManager>,
    ): ObserveAttachmentDraftsUseCase =
        ObserveAttachmentDraftsUseCaseImpl(messageAttachmentsDraftRepository.value, uploadManager.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("publishAttachments")
    fun provideCellsPublishAttachments(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
    ): PublishAttachmentsUseCase =
        PublishAttachmentsUseCaseImpl(cellsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("selfTeamIdProvider")
    fun provideCellsSelfTeamIdProvider(
        @LegacyCellsBinding("userId")
        userId: UserId,
        @LegacyCellsBinding("usersRepository")
        usersRepository: Lazy<CellUsersRepository>,
    ): SelfTeamIdProvider =
        SelfTeamIdProviderImpl(userId, usersRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("observeFiles")
    fun provideCellsObserveFiles(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
        @LegacyCellsBinding("cellsConversationRepository")
        cellsConversationRepository: Lazy<CellConversationRepository>,
        @LegacyCellsBinding("cellAttachmentsRepository")
        cellAttachmentsRepository: Lazy<CellAttachmentsRepository>,
        @LegacyCellsBinding("usersRepository")
        usersRepository: Lazy<CellUsersRepository>,
        @LegacyCellsBinding("selfTeamIdProvider")
        selfTeamIdProvider: Lazy<SelfTeamIdProvider>,
    ): GetPaginatedNodesUseCase =
        GetPaginatedNodesUseCaseImpl(
            cellsRepository.value,
            cellsConversationRepository.value,
            cellAttachmentsRepository.value,
            usersRepository.value,
            selfTeamIdProvider.value,
        )

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("observePagedFiles")
    fun provideCellsObservePagedFiles(
        @LegacyCellsBinding("observeFiles")
        observeFiles: Lazy<GetPaginatedNodesUseCase>,
    ): GetCellFilesPagedUseCase =
        GetCellFilesPagedUseCaseImpl(observeFiles.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("downloadCellFile")
    fun provideCellsDownloadCellFile(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
        @LegacyCellsBinding("cellAttachmentsRepository")
        cellAttachmentsRepository: Lazy<CellAttachmentsRepository>,
    ): DownloadCellFileUseCase =
        DownloadCellFileUseCaseImpl(cellsRepository.value, cellAttachmentsRepository.value)

    @Provides
    @LegacyCellsBinding("fileDownloader")
    fun provideCellsFileDownloader(
        owner: CellsOwnerPorts,
    ): FileDownloader =
        fileDownloader(httpClient = owner.downloadHttpClient)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("refreshAsset")
    fun provideCellsRefreshAsset(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
        @LegacyCellsBinding("cellAttachmentsRepository")
        cellAttachmentsRepository: Lazy<CellAttachmentsRepository>,
    ): RefreshCellAssetStateUseCase =
        RefreshCellAssetStateUseCaseImpl(cellsRepository.value, cellAttachmentsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("deleteAttachmentsUseCase")
    fun provideCellsDeleteAttachmentsUseCase(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
        @LegacyCellsBinding("cellAttachmentsRepository")
        cellAttachmentsRepository: Lazy<CellAttachmentsRepository>,
    ): DeleteMessageAttachmentsUseCase =
        DeleteMessageAttachmentsUseCaseImpl(cellsRepository.value, cellAttachmentsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("deleteCellAssetUseCase")
    fun provideCellsDeleteCellAssetUseCase(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
        @LegacyCellsBinding("cellAttachmentsRepository")
        cellAttachmentsRepository: Lazy<CellAttachmentsRepository>,
    ): DeleteCellAssetUseCase =
        DeleteCellAssetUseCaseImpl(cellsRepository.value, cellAttachmentsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("createPublicLinkUseCase")
    fun provideCellsCreatePublicLinkUseCase(
        @LegacyCellsBinding("cellsCredentialsProvider")
        cellsCredentialsProvider: Lazy<CellsCredentialsProvider>,
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
    ): CreatePublicLinkUseCase =
        CreatePublicLinkUseCaseImpl(cellsCredentialsProvider.value, cellsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("getPublicLinkUseCase")
    fun provideCellsGetPublicLinkUseCase(
        @LegacyCellsBinding("cellsCredentialsProvider")
        cellsCredentialsProvider: Lazy<CellsCredentialsProvider>,
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
    ): GetPublicLinkUseCase =
        GetPublicLinkUseCaseImpl(cellsCredentialsProvider.value, cellsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("deletePublicLinkUseCase")
    fun provideCellsDeletePublicLinkUseCase(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
    ): DeletePublicLinkUseCase =
        DeletePublicLinkUseCaseImpl(cellsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("retryAttachmentUpload")
    fun provideCellsRetryAttachmentUpload(
        @LegacyCellsBinding("coroutineScope")
        coroutineScope: CoroutineScope,
        @LegacyCellsBinding("messageAttachmentsDraftRepository")
        messageAttachmentsDraftRepository: Lazy<MessageAttachmentDraftRepository>,
        @LegacyCellsBinding("uploadManager")
        uploadManager: Lazy<CellUploadManager>,
    ): RetryAttachmentUploadUseCase =
        RetryAttachmentUploadUseCaseImpl(uploadManager.value, messageAttachmentsDraftRepository.value, coroutineScope)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("createFolderUseCase")
    fun provideCellsCreateFolderUseCase(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
    ): CreateFolderUseCase =
        CreateFolderUseCaseImpl(cellsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("createSpreadsheetFileUseCase")
    fun provideCellsCreateSpreadsheetFileUseCase(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
    ): CreateSpreadsheetFileUseCase =
        CreateSpreadsheetFileUseCaseImpl(cellsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("createPresentationFileUseCase")
    fun provideCellsCreatePresentationFileUseCase(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
    ): CreatePresentationFileUseCase =
        CreatePresentationFileUseCaseImpl(cellsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("createDocumentFileUseCase")
    fun provideCellsCreateDocumentFileUseCase(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
    ): CreateDocumentFileUseCase =
        CreateDocumentFileUseCaseImpl(cellsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("moveNodeUseCase")
    fun provideCellsMoveNodeUseCase(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
    ): MoveNodeUseCase =
        MoveNodeUseCaseImpl(cellsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("getFoldersUseCase")
    fun provideCellsGetFoldersUseCase(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
    ): GetFoldersUseCase =
        GetFoldersUseCaseImpl(cellsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("restoreNodeFromRecycleBin")
    fun provideCellsRestoreNodeFromRecycleBin(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
        @LegacyCellsBinding("cellAttachmentsRepository")
        cellAttachmentsRepository: Lazy<CellAttachmentsRepository>,
    ): RestoreNodeFromRecycleBinUseCase =
        RestoreNodeFromRecycleBinUseCaseImpl(cellsRepository.value, cellAttachmentsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("getAllTags")
    fun provideCellsGetAllTags(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
    ): GetAllTagsUseCase =
        GetAllTagsUseCaseImpl(cellsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("updateNodeTagsUseCase")
    fun provideCellsUpdateNodeTagsUseCase(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
    ): UpdateNodeTagsUseCase =
        UpdateNodeTagsUseCaseImpl(cellsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("removeNodeTagsUseCase")
    fun provideCellsRemoveNodeTagsUseCase(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
    ): RemoveNodeTagsUseCase =
        RemoveNodeTagsUseCaseImpl(cellsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("renameNodeUseCase")
    fun provideCellsRenameNodeUseCase(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
        @LegacyCellsBinding("cellAttachmentsRepository")
        cellAttachmentsRepository: Lazy<CellAttachmentsRepository>,
    ): RenameNodeUseCase =
        RenameNodeUseCaseImpl(cellsRepository.value, cellAttachmentsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("isCellAvailable")
    fun provideCellsIsCellAvailable(
        @LegacyCellsBinding("cellsConversationRepository")
        cellsConversationRepository: Lazy<CellConversationRepository>,
    ): IsAtLeastOneCellAvailableUseCase =
        IsAtLeastOneCellAvailableUseCaseImpl(cellsConversationRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("observeIsCellAvailable")
    fun provideCellsObserveIsCellAvailable(
        @LegacyCellsBinding("cellsConversationRepository")
        cellsConversationRepository: Lazy<CellConversationRepository>,
    ): ObserveIsAtLeastOneCellAvailableUseCase =
        ObserveIsAtLeastOneCellAvailableUseCaseImpl(cellsConversationRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("getMessageAttachmentUseCase")
    fun provideCellsGetMessageAttachmentUseCase(
        @LegacyCellsBinding("cellAttachmentsRepository")
        cellAttachmentsRepository: Lazy<CellAttachmentsRepository>,
    ): GetMessageAttachmentUseCase =
        GetMessageAttachmentUseCaseImpl(cellAttachmentsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("getOwnersUseCase")
    fun provideCellsGetOwnersUseCase(
        @LegacyCellsBinding("usersRepository")
        usersRepository: Lazy<CellUsersRepository>,
    ): GetOwnersUseCase =
        GetOwnersUseCaseImpl(usersRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("getCellGroupConversationsUseCase")
    fun provideCellsGetCellGroupConversationsUseCase(
        @LegacyCellsBinding("cellsConversationRepository")
        cellsConversationRepository: Lazy<CellConversationRepository>,
    ): GetCellGroupConversationsUseCase =
        GetCellGroupConversationsUseCaseImpl(cellsConversationRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("getCellConversationsPagedUseCase")
    fun provideCellsGetCellConversationsPagedUseCase(
        @LegacyCellsBinding("cellsConversationRepository")
        cellsConversationRepository: Lazy<CellConversationRepository>,
    ): GetCellConversationsPagedUseCase =
        GetCellConversationsPagedUseCaseImpl(cellsConversationRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("getMessageAttachmentsUseCase")
    fun provideCellsGetMessageAttachmentsUseCase(
        @LegacyCellsBinding("cellAttachmentsRepository")
        cellAttachmentsRepository: Lazy<CellAttachmentsRepository>,
    ): GetMessageAttachmentsUseCase =
        GetMessageAttachmentsUseCaseImpl(cellAttachmentsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("getCellFileUseCase")
    fun provideCellsGetCellFileUseCase(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
    ): GetCellFileUseCase =
        GetCellFileUseCaseImpl(cellsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("createPublicLinkPasswordUseCase")
    fun provideCellsCreatePublicLinkPasswordUseCase(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
    ): CreatePublicLinkPasswordUseCase =
        CreatePublicLinkPasswordUseCaseImpl(cellsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("updatePublicLinkPasswordUseCase")
    fun provideCellsUpdatePublicLinkPasswordUseCase(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
    ): UpdatePublicLinkPasswordUseCase =
        UpdatePublicLinkPasswordUseCaseImpl(cellsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("getPublicLinkPassword")
    fun provideCellsGetPublicLinkPassword(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
    ): GetPublicLinkPasswordUseCase =
        GetPublicLinkPasswordUseCaseImpl(cellsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("setPublicLinkExpiration")
    fun provideCellsSetPublicLinkExpiration(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
    ): SetPublicLinkExpirationUseCase =
        SetPublicLinkExpirationUseCaseImpl(cellsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("getEditorUrl")
    fun provideCellsGetEditorUrl(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
    ): GetEditorUrlUseCase =
        GetEditorUrlUseCaseImpl(cellsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("getPdfPreviewUrl")
    fun provideCellsGetPdfPreviewUrl(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
    ): GetPdfPreviewUrlUseCase =
        GetPdfPreviewUrlUseCaseImpl(cellsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("getNodeVersions")
    fun provideCellsGetNodeVersions(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
    ): GetNodeVersionsUseCase =
        GetNodeVersionsUseCaseImpl(cellsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("restoreNodeVersion")
    fun provideCellsRestoreNodeVersion(
        @LegacyCellsBinding("cellsRepository")
        cellsRepository: Lazy<CellsRepository>,
    ): RestoreNodeVersionUseCase =
        RestoreNodeVersionUseCaseImpl(cellsRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("downloadCellVersion")
    fun provideCellsDownloadCellVersion(
        @LegacyCellsBinding("fileDownloader")
        fileDownloader: Lazy<FileDownloader>,
    ): DownloadCellVersionUseCase =
        DownloadCellVersionUseCaseImpl(fileDownloader.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("getCellConfig")
    fun provideCellsGetCellConfig(
        @LegacyCellsBinding("cellConfigRepository")
        cellConfigRepository: Lazy<CellConfigRepository>,
    ): GetWireCellConfigurationUseCase =
        GetWireCellConfigurationUseCaseImpl(cellConfigRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("saveOfflineFile")
    fun provideCellsSaveOfflineFile(
        @LegacyCellsBinding("cellFileRepository")
        cellFileRepository: Lazy<CellFileRepository>,
    ): SaveOfflineFileUseCase =
        SaveOfflineFileUseCaseImpl(cellFileRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("deleteOfflineFile")
    fun provideCellsDeleteOfflineFile(
        @LegacyCellsBinding("cellFileRepository")
        cellFileRepository: Lazy<CellFileRepository>,
    ): DeleteOfflineFileUseCase =
        DeleteOfflineFileUseCaseImpl(cellFileRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("observeOfflineFiles")
    fun provideCellsObserveOfflineFiles(
        @LegacyCellsBinding("cellFileRepository")
        cellFileRepository: Lazy<CellFileRepository>,
    ): ObserveOfflineFilesUseCase =
        ObserveOfflineFilesUseCaseImpl(cellFileRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("observeOfflineFilesByConversation")
    fun provideCellsObserveOfflineFilesByConversation(
        @LegacyCellsBinding("cellFileRepository")
        cellFileRepository: Lazy<CellFileRepository>,
    ): ObserveOfflineFilesByConversationUseCase =
        ObserveOfflineFilesByConversationUseCaseImpl(cellFileRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("getOfflineFile")
    fun provideCellsGetOfflineFile(
        @LegacyCellsBinding("cellFileRepository")
        cellFileRepository: Lazy<CellFileRepository>,
    ): GetOfflineFileUseCase =
        GetOfflineFileUseCaseImpl(cellFileRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("getConversationName")
    fun provideCellsGetConversationName(
        @LegacyCellsBinding("cellsConversationRepository")
        cellsConversationRepository: Lazy<CellConversationRepository>,
    ): GetConversationNameUseCase =
        GetConversationNameUseCaseImpl(cellsConversationRepository.value)

    @Provides
    @SingleIn(CellsFacadeLifetime::class)
    @LegacyCellsBinding("getUserName")
    fun provideCellsGetUserName(
        @LegacyCellsBinding("usersRepository")
        usersRepository: Lazy<CellUsersRepository>,
    ): GetUserNameUseCase =
        GetUserNameUseCaseImpl(usersRepository.value)
}

internal expect fun createLegacyCellsGraph(dependencies: LegacyCellsDependencies, owner: CellsOwnerPorts): LegacyCellsApi
