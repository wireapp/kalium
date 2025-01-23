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

import com.wire.cells.WireCellsClient
import com.wire.cells.s3.CellsS3Client
import com.wire.cells.s3.S3ClientCredentials
import com.wire.cells.s3.cellsS3Client
import com.wire.kalium.logic.feature.cells.usecase.DeleteFromCellUseCase
import com.wire.kalium.logic.feature.cells.usecase.DeleteFromCellUseCaseImpl
import com.wire.kalium.logic.feature.cells.usecase.ListCellFilesUseCase
import com.wire.kalium.logic.feature.cells.usecase.ListCellFilesUseCaseImpl
import com.wire.kalium.logic.feature.cells.usecase.UploadToCellUseCase
import com.wire.kalium.logic.feature.cells.usecase.UploadToCellUseCaseImpl

class WireCellsScope {
    private val s3ClientCredentials: S3ClientCredentials
        get() = S3ClientCredentials(
                serverUrl = "https://service.zeta.pydiocells.com",
                accessToken = "<YOUR TOKEN>",
                gatewaySecret = "gatewaysecret",
                regionName = "us-east-1",
                bucketName = "io"
            )

    private val s3Client: CellsS3Client
        get() = cellsS3Client(s3ClientCredentials)

    private val wireCellsClient: WireCellsClient
        get() = WireCellsClient(s3Client)

    val uploadToCellUseCase: UploadToCellUseCase
        get() = UploadToCellUseCaseImpl(wireCellsClient)

    val listCellFilesUseCase: ListCellFilesUseCase
        get() = ListCellFilesUseCaseImpl(wireCellsClient)

    val deleteFromCellUseCase: DeleteFromCellUseCase
        get() = DeleteFromCellUseCaseImpl(wireCellsClient)
}
