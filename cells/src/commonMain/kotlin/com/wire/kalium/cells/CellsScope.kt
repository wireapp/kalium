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

import com.wire.kalium.cells.data.CellUploadManagerImpl
import com.wire.kalium.cells.data.CellsApiImpl
import com.wire.kalium.cells.data.CellsAwsClient
import com.wire.kalium.cells.data.CellsDataSource
import com.wire.kalium.cells.data.MessageAttachmentDraftDataSource
import com.wire.kalium.cells.data.cellsAwsClient
import com.wire.kalium.cells.domain.CellUploadManager
import com.wire.kalium.cells.domain.CellsApi
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.NodeServiceBuilder
import com.wire.kalium.cells.domain.model.CellsCredentials
import com.wire.kalium.cells.domain.usecase.AddAttachmentDraftUseCase
import com.wire.kalium.cells.domain.usecase.AddAttachmentDraftUseCaseImpl
import com.wire.kalium.cells.domain.usecase.ObserveAttachmentDraftsUseCase
import com.wire.kalium.cells.domain.usecase.ObserveAttachmentDraftsUseCaseImpl
import com.wire.kalium.cells.domain.usecase.ObserveCellFilesUseCase
import com.wire.kalium.cells.domain.usecase.ObserveCellFilesUseCaseImpl
import com.wire.kalium.cells.domain.usecase.PublishAttachmentsUseCase
import com.wire.kalium.cells.domain.usecase.PublishAttachmentsUseCaseImpl
import com.wire.kalium.cells.domain.usecase.RemoveAttachmentDraftUseCase
import com.wire.kalium.cells.domain.usecase.RemoveAttachmentDraftUseCaseImpl
import com.wire.kalium.cells.domain.usecase.SetWireCellForConversationUseCase
import com.wire.kalium.cells.sdk.kmp.api.NodeServiceApi
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.messageattachment.MessageAttachmentDraftDao
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

public class CellsScope(
    private val cellsClient: HttpClient,
    private val attachmentDraftDao: MessageAttachmentDraftDao,
    private val conversationsDAO: ConversationDAO,
) : CoroutineScope {

    internal companion object {
        // Temporary hardcoded root cell
        const val ROOT_CELL = "wire-cells-android"
    }

    override val coroutineContext: CoroutineContext = SupervisorJob()

    // Temporary hardcoded credentials and server URL
    private val cellClientCredentials: CellsCredentials
        get() = CellsCredentials(
            serverUrl = "https://service.zeta.pydiocells.com",
            accessToken = "<your-access-token>",
            gatewaySecret = "gatewaysecret",
        )

    private val cellAwsClient: CellsAwsClient
        get() = cellsAwsClient(cellClientCredentials)

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
            awsClient = cellAwsClient
        )

    private val messageAttachmentsDraftRepository: MessageAttachmentDraftRepository
        get() = MessageAttachmentDraftDataSource(attachmentDraftDao)

    public val uploadManager: CellUploadManager by lazy {
        CellUploadManagerImpl(
            repository = cellsRepository,
            uploadScope = this,
        )
    }

    public val addAttachment: AddAttachmentDraftUseCase
        get() = AddAttachmentDraftUseCaseImpl(uploadManager, messageAttachmentsDraftRepository, this)

    public val removeAttachment: RemoveAttachmentDraftUseCase
        get() = RemoveAttachmentDraftUseCaseImpl(uploadManager, messageAttachmentsDraftRepository, cellsRepository)

    public val observeAttachments: ObserveAttachmentDraftsUseCase
        get() = ObserveAttachmentDraftsUseCaseImpl(messageAttachmentsDraftRepository, uploadManager)

    public val publishAttachments: PublishAttachmentsUseCase
        get() = PublishAttachmentsUseCaseImpl(cellsRepository, messageAttachmentsDraftRepository, cellClientCredentials)

    public val observeFiles: ObserveCellFilesUseCase
        get() = ObserveCellFilesUseCaseImpl(conversationsDAO, cellsRepository)

    public val enableWireCell: SetWireCellForConversationUseCase
        get() = SetWireCellForConversationUseCase(conversationsDAO)
}
