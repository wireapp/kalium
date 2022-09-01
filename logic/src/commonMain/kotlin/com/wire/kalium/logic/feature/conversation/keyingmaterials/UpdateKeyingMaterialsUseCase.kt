package com.wire.kalium.logic.feature.conversation.keyingmaterials

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.UpdateKeyingMaterialThresholdProvider
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.foldToEitherWhileRight

sealed class UpdateKeyingMaterialsResult {

    object Success : UpdateKeyingMaterialsResult()
    class Failure(val failure: CoreFailure) : UpdateKeyingMaterialsResult()

}

interface UpdateKeyingMaterialsUseCase {
    suspend operator fun invoke(): UpdateKeyingMaterialsResult
}

class UpdateKeyingMaterialsUseCaseImpl(
    val mlsConversationRepository: MLSConversationRepository,
    private val updateKeyingMaterialThresholdProvider: UpdateKeyingMaterialThresholdProvider
) : UpdateKeyingMaterialsUseCase {
    override suspend fun invoke(): UpdateKeyingMaterialsResult = mlsConversationRepository
        .getMLSGroupsRequiringKeyingMaterialUpdate(updateKeyingMaterialThresholdProvider.keyingMaterialUpdateThreshold)
        .flatMap { groups ->
            groups.map { mlsConversationRepository.updateKeyingMaterial(it) }
                .foldToEitherWhileRight(Unit) { value, _ -> value }
        }.fold(
            { UpdateKeyingMaterialsResult.Failure(it) },
            { UpdateKeyingMaterialsResult.Success }
        )

}
