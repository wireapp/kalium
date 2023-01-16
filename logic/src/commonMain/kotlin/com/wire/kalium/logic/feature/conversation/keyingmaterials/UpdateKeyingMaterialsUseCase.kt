package com.wire.kalium.logic.feature.conversation.keyingmaterials

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.UpdateKeyingMaterialThresholdProvider
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

sealed class UpdateKeyingMaterialsResult {

    object Success : UpdateKeyingMaterialsResult()
    class Failure(val failure: CoreFailure) : UpdateKeyingMaterialsResult()

}

/**
 * This use case will check if the number of keying materials is below the minimum threshold and will
 * upload new keying materials if needed for the mls conversations of the user.
 */
interface UpdateKeyingMaterialsUseCase {
    suspend operator fun invoke(): UpdateKeyingMaterialsResult
}

class UpdateKeyingMaterialsUseCaseImpl(
    val mlsConversationRepository: MLSConversationRepository,
    private val updateKeyingMaterialThresholdProvider: UpdateKeyingMaterialThresholdProvider,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : UpdateKeyingMaterialsUseCase {
    override suspend fun invoke(): UpdateKeyingMaterialsResult = withContext(dispatcher.default) {
        mlsConversationRepository
            .getMLSGroupsRequiringKeyingMaterialUpdate(updateKeyingMaterialThresholdProvider.keyingMaterialUpdateThreshold)
            .flatMap { groups ->
                groups.map { mlsConversationRepository.updateKeyingMaterial(it) }
                    .foldToEitherWhileRight(Unit) { value, _ -> value }
            }.fold(
                { UpdateKeyingMaterialsResult.Failure(it) },
                { UpdateKeyingMaterialsResult.Success }
            )
    }
}
