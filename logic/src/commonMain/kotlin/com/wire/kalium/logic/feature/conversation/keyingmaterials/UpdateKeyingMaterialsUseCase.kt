package com.wire.kalium.logic.feature.conversation.keyingmaterials

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

// TODO: there are some edge cases and optimisations points to consider for M5-> please see: https://wearezeta.atlassian.net/browse/AR-1633
internal val KEYING_MATERIAL_UPDATE_THRESHOLD = 90.days

class UpdateKeyingMaterialsUseCaseImpl(
    val mlsConversationRepository: MLSConversationRepository
) : UpdateKeyingMaterialsUseCase {
    override suspend fun invoke(): UpdateKeyingMaterialsResult =
        mlsConversationRepository.getMLSGroupsRequiringKeyingMaterialUpdate(KEYING_MATERIAL_UPDATE_THRESHOLD).map { groups ->
            groups.onEach { groupId ->
                mlsConversationRepository.updateKeyingMaterial(groupId)
            }
        }.fold(
            { UpdateKeyingMaterialsResult.Failure(it) },
            { UpdateKeyingMaterialsResult.Success }
        )

}
