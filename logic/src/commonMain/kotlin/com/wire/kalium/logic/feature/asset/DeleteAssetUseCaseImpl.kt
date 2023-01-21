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

