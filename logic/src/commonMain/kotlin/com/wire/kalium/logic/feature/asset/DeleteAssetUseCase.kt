package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.user.UserAssetId

class DeleteAssetUseCase(private val assetRepository: AssetRepository) {

    suspend operator fun invoke(assetKey: UserAssetId) {
        assetRepository.deleteAssetLocally(assetKey.value)
    }

}
