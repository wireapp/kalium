package com.wire.kalium.logic.feature.conversation.keyingmaterials

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.UpdateKeyingMaterialsThresholdProvider
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map

sealed class UpdateKeyingMaterialsResult {

    object Success : UpdateKeyingMaterialsResult()
    class Failure(val failure: CoreFailure) : UpdateKeyingMaterialsResult()

}

interface UpdateKeyingMaterialsUseCase {
    suspend operator fun invoke(): UpdateKeyingMaterialsResult
}

class UpdateKeyingMaterialsUseCaseImpl(
    val mlsConversationRepository: MLSConversationRepository,
    private val updateKeyingMaterialsThresholdProvider: UpdateKeyingMaterialsThresholdProvider
) : UpdateKeyingMaterialsUseCase {
    override suspend fun invoke(): UpdateKeyingMaterialsResult =
        mlsConversationRepository
            .getMLSGroupsRequiringKeyingMaterialUpdate(updateKeyingMaterialsThresholdProvider.keyingMaterialUpdateThreshold).map { groups ->
                groups.onEach { groupId ->
                    mlsConversationRepository.updateKeyingMaterial(groupId)
                }
            }.fold(
                { UpdateKeyingMaterialsResult.Failure(it) },
                { UpdateKeyingMaterialsResult.Success }
            )

}
