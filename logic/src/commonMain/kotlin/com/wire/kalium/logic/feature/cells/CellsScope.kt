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
package com.wire.kalium.logic.feature.cells

import com.wire.kalium.logic.GlobalKaliumScope
import com.wire.kalium.logic.feature.cells.usecase.CancelDraftUseCase
import com.wire.kalium.logic.feature.cells.usecase.CancelDraftUseCaseImpl
import com.wire.kalium.logic.feature.cells.usecase.DeleteCellFileUseCase
import com.wire.kalium.logic.feature.cells.usecase.DeleteCellFileUseCaseImpl
import com.wire.kalium.logic.feature.cells.usecase.GetCellFilesUseCase
import com.wire.kalium.logic.feature.cells.usecase.GetCellFilesUseCaseImpl
import com.wire.kalium.logic.feature.cells.usecase.PublishDraftUseCase
import com.wire.kalium.logic.feature.cells.usecase.PublishDraftUseCaseImpl
import com.wire.kalium.logic.feature.cells.usecase.UploadToCellUseCase
import com.wire.kalium.logic.feature.cells.usecase.UploadToCellUseCaseImpl
import com.wire.kalium.network.cells.aws.CellsAwsClient
import com.wire.kalium.network.cells.aws.CellsCredentials
import com.wire.kalium.network.cells.aws.cellsAwsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

class CellsScope internal constructor(
    private val globalScope: GlobalKaliumScope,
) : CoroutineScope {

    override val coroutineContext: CoroutineContext = SupervisorJob()

    // Temporary hardcoded credentials and server URL
    private val cellClientCredentials: CellsCredentials
        get() = CellsCredentials(
            serverUrl = "https://service.zeta.pydiocells.com",
            accessToken = "<access_token>",
            gatewaySecret = "<gateway_secret>",
        )

    private val cellAwsClient: CellsAwsClient
        get() = cellsAwsClient(cellClientCredentials)

    private val cellsRepository: CellsRepository
        get() = CellsDataSource(
            cellsApi = globalScope.unboundNetworkContainer.cellsApi(cellClientCredentials),
            awsClient = cellAwsClient
        )

    val getCellFiles: GetCellFilesUseCase
        get() = GetCellFilesUseCaseImpl(cellsRepository)

    val uploadToCell: UploadToCellUseCase
        get() = UploadToCellUseCaseImpl(
            scope = this,
            cellsRepository = cellsRepository
        )

    val deleteFromCell: DeleteCellFileUseCase
        get() = DeleteCellFileUseCaseImpl(cellsRepository)

    val cancelDraft: CancelDraftUseCase
        get() = CancelDraftUseCaseImpl(cellsRepository)

    val publishDraft: PublishDraftUseCase
        get() = PublishDraftUseCaseImpl(cellsRepository)
}
