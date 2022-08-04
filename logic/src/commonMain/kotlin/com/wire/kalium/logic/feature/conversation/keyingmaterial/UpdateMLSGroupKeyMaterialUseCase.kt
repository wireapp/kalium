package com.wire.kalium.logic.feature.conversation.keyingmaterial

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import kotlin.time.Duration.Companion.days


sealed class UpdateKeyingMaterialsResult {

    object Success : UpdateKeyingMaterialsResult()
    class Failure(val failure: CoreFailure) : UpdateKeyingMaterialsResult()

}

interface UpdateKeyingMaterialsUseCase {
    suspend operator fun invoke(): UpdateKeyingMaterialsResult
}

internal val KEYING_MATERIAL_UPDATE_THRESHOLD = 90.days

class UpdateKeyingMaterialsUseCaseImpl(
    val mlsConversationRepository: MLSConversationRepository
) : UpdateKeyingMaterialsUseCase {
    override suspend fun invoke(): UpdateKeyingMaterialsResult =
        mlsConversationRepository.getConversationsByKeyMaterialUpdate(KEYING_MATERIAL_UPDATE_THRESHOLD).map {
            it.map { groupId ->
                mlsConversationRepository.updateKeyingMaterial(groupId)
            }
            UpdateKeyingMaterialsResult.Success
        }.fold(
            { UpdateKeyingMaterialsResult.Failure(it) },
            { UpdateKeyingMaterialsResult.Success }
        )

}
