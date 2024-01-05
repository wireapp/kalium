/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.user.AssetId
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Use case allowing to delete asset locally, without sending it to other clients.
 * the asset upload succeeded, but instead that the creation and persistence of the initial asset message succeeded.
 *
 * @param assetId the id of the asset to delete
 */

interface DeleteAssetUseCase {
    suspend operator fun invoke(assetId: AssetId)
}

internal class DeleteAssetUseCaseImpl(
    private val assetRepository: AssetRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : DeleteAssetUseCase {

    override suspend operator fun invoke(assetKey: AssetId) {
        withContext(dispatcher.default) {
            assetRepository.deleteAssetLocally(assetKey.value)
        }
    }

}
