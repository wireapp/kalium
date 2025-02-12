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
import com.wire.kalium.cells.data.CellsApi
import com.wire.kalium.cells.data.CellsApiImpl
import com.wire.kalium.cells.data.CellsAwsClient
import com.wire.kalium.cells.data.CellsDataSource
import com.wire.kalium.cells.data.cellsAwsClient
import com.wire.kalium.cells.domain.CellUploadManager
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.model.CellsCredentials
import com.wire.kalium.cells.domain.usecase.CancelDraftUseCase
import com.wire.kalium.cells.domain.usecase.CancelDraftUseCaseImpl
import com.wire.kalium.cells.domain.usecase.DeleteCellFileUseCase
import com.wire.kalium.cells.domain.usecase.DeleteCellFileUseCaseImpl
import com.wire.kalium.cells.domain.usecase.GetCellFilesUseCase
import com.wire.kalium.cells.domain.usecase.GetCellFilesUseCaseImpl
import com.wire.kalium.cells.domain.usecase.PublishDraftUseCase
import com.wire.kalium.cells.domain.usecase.PublishDraftUseCaseImpl
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

public class CellsScope(
    private val cellsClient: HttpClient,
) : CoroutineScope {

    override val coroutineContext: CoroutineContext = SupervisorJob()

    // Temporary hardcoded credentials and server URL
    private val cellClientCredentials: CellsCredentials
        get() = CellsCredentials(
            serverUrl = "https://service.zeta.pydiocells.com",
            accessToken = "mBzSPjZ1qH7weLqHlNK9_W5HNUN0zdESyvhL4KqlhhM.0TUuMHKucKMCfC337jaUof-gdjODmCj2gGML5INWc8w",
            gatewaySecret = "gatewaysecret",
        )

    private val cellAwsClient: CellsAwsClient
        get() = cellsAwsClient(cellClientCredentials)

    private val cellsApi: CellsApi
        get() = CellsApiImpl(
            credentials = cellClientCredentials,
            httpClient = cellsClient
        )

    private val cellsRepository: CellsRepository
        get() = CellsDataSource(
            cellsApi = cellsApi,
            awsClient = cellAwsClient
        )

    public val uploadManager: CellUploadManager by lazy {
        CellUploadManagerImpl(
            repository = cellsRepository,
            uploadScope = this,
        )
    }

    public val getCellFiles: GetCellFilesUseCase
        get() = GetCellFilesUseCaseImpl(cellsRepository)

    public val deleteFromCell: DeleteCellFileUseCase
        get() = DeleteCellFileUseCaseImpl(cellsRepository)

    public val cancelDraft: CancelDraftUseCase
        get() = CancelDraftUseCaseImpl(cellsRepository)

    public val publishDraft: PublishDraftUseCase
        get() = PublishDraftUseCaseImpl(cellsRepository)
}
